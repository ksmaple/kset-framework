package com.kset.auth.gateway;

import com.kset.auth.config.KsetAuthProperties;
import com.kset.auth.core.AuthRequest;
import com.kset.auth.core.AuthResult;
import com.kset.auth.core.AuthRuleMatch;
import com.kset.auth.core.AuthRuleResolver;
import com.kset.auth.core.AuthSchemes;
import com.kset.auth.core.LoginAuthService;
import com.kset.auth.spi.LoginUserHeaderCodec;
import com.kset.common.auth.AuthHeaders;
import com.kset.common.auth.LoginUser;
import com.kset.common.trace.TraceHeaders;
import com.kset.common.utils.sign.KsetSignUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class LoginAuthGatewayFilter implements GlobalFilter, Ordered {

    private static final Set<String> IDENTITY_HEADERS = Set.of(
            AuthHeaders.AUTH_SUBJECT, AuthHeaders.LOGIN_CONTEXT,
            AuthHeaders.USER_ID, AuthHeaders.USER_NAME, AuthHeaders.REAL_NAME,
            AuthHeaders.NICK_NAME, AuthHeaders.AVATAR, AuthHeaders.MOBILE,
            AuthHeaders.EMAIL, AuthHeaders.TENANT_ID, AuthHeaders.ORG_ID,
            AuthHeaders.DEPT_ID, AuthHeaders.ROLES, AuthHeaders.PERMISSIONS,
            AuthHeaders.DEVICE_ID, AuthHeaders.DEVICE_TYPE, AuthHeaders.CLIENT_TYPE,
            AuthHeaders.CLIENT_VERSION, AuthHeaders.IP, AuthHeaders.LANGUAGE,
            AuthHeaders.TIME_ZONE, AuthHeaders.LOGIN_TIME);

    private final KsetAuthProperties properties;
    private final LoginAuthService authService;
    private final LoginUserHeaderCodec headerCodec;
    private final GatewayAuthFailureHandler failureHandler;
    private final Set<String> identityHeaders;

    public LoginAuthGatewayFilter(KsetAuthProperties properties,
                                  LoginAuthService authService,
                                  LoginUserHeaderCodec headerCodec,
                                  GatewayAuthFailureHandler failureHandler) {
        this.properties = properties;
        this.authService = authService;
        this.headerCodec = headerCodec;
        this.failureHandler = failureHandler;
        this.identityHeaders = new HashSet<>();
        IDENTITY_HEADERS.forEach(header -> identityHeaders.add(header.toLowerCase(Locale.ROOT)));
        if (properties.getSubjectHeader() != null) {
            identityHeaders.add(properties.getSubjectHeader().toLowerCase(Locale.ROOT));
        }
        properties.getRules().stream()
                .map(KsetAuthProperties.AuthRule::getTrustedHeaderName)
                .filter(header -> header != null && !header.isBlank())
                .map(header -> header.toLowerCase(Locale.ROOT))
                .forEach(identityHeaders::add);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!properties.getGateway().isEnabled()) {
            return chain.filter(exchange);
        }
        AuthRequest request = authRequest(exchange, null);
        AuthRuleMatch match = authService.resolve(request);
        if (match != null && AuthSchemes.SIGNATURE.equalsIgnoreCase(match.getScheme())) {
            String contentType = exchange.getRequest().getHeaders().getFirst("Content-Type");
            if (contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("multipart/")) {
                exchange.getResponse().setStatusCode(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
                return exchange.getResponse().setComplete();
            }
            if (exchange.getRequest().getQueryParams().values().stream()
                    .anyMatch(values -> values.size() > 1)) {
                exchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
                return exchange.getResponse().setComplete();
            }
            return signedExchange(exchange)
                    .flatMap(signed -> authenticate(signed.exchange(), chain,
                            authRequest(signed.exchange(), signed.bodySha256()), match))
                    .onErrorResume(DataBufferLimitException.class, error -> {
                        exchange.getResponse().setStatusCode(HttpStatus.PAYLOAD_TOO_LARGE);
                        return exchange.getResponse().setComplete();
                    });
        }
        return authenticate(exchange, chain, request, match);
    }

    private Mono<Void> authenticate(ServerWebExchange exchange, GatewayFilterChain chain,
                                    AuthRequest request, AuthRuleMatch match) {
        return Mono.fromCallable(() -> authService.authenticate(request, match))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(result -> continueFilter(exchange, chain, result));
    }

    private AuthRequest authRequest(ServerWebExchange exchange, String bodySha256) {
        return new AuthRequest(exchange.getRequest().getURI().getRawPath(),
                name -> exchange.getRequest().getHeaders().getFirst(name),
                AuthRuleResolver.SOURCE_GATEWAY,
                exchange.getRequest().getMethod() != null ? exchange.getRequest().getMethod().name() : null,
                queryParams(exchange), bodySha256);
    }

    private Mono<SignedExchange> signedExchange(ServerWebExchange exchange) {
        int maxBytes = properties.getAppKey().getMaxSignedBodyBytes();
        return DataBufferUtils.join(exchange.getRequest().getBody(), maxBytes)
                .map(buffer -> {
                    try {
                        byte[] body = new byte[buffer.readableByteCount()];
                        buffer.read(body);
                        return replayableExchange(exchange, body);
                    } finally {
                        DataBufferUtils.release(buffer);
                    }
                })
                .switchIfEmpty(Mono.fromSupplier(() -> replayableExchange(exchange, new byte[0])));
    }

    private SignedExchange replayableExchange(ServerWebExchange exchange, byte[] body) {
        ServerHttpRequest request = new ServerHttpRequestDecorator(exchange.getRequest()) {
            @Override
            public Flux<DataBuffer> getBody() {
                return Flux.defer(() -> Flux.just(exchange.getResponse().bufferFactory().wrap(body)));
            }
        };
        return new SignedExchange(exchange.mutate().request(request).build(), KsetSignUtil.sha256(body));
    }

    private record SignedExchange(ServerWebExchange exchange, String bodySha256) {
    }

    private Mono<Void> continueFilter(ServerWebExchange exchange, GatewayFilterChain chain, AuthResult result) {
        ServerHttpRequest.Builder requestBuilder = exchange.getRequest().mutate()
                .headers(headers -> headers.keySet().removeIf(this::isIdentityHeader));
        if (result.isPermitAll()) {
            return chain.filter(exchange.mutate().request(requestBuilder.build()).build());
        }
        if (!result.isAuthenticated() || result.getUser().isEmpty()) {
            try {
                log.warn("auth result=REJECTED source=gateway method={} path={} businessCode={} traceId={}",
                        exchange.getRequest().getMethod(), exchange.getRequest().getURI().getRawPath(),
                        result.getCode(), exchange.getAttribute(TraceHeaders.TRACE_ID_KEY));
            } catch (RuntimeException ignored) {
                // Authentication response must not depend on logging.
            }
            return failureHandler.handle(exchange, result.getCode(), result.getMessage());
        }
        LoginUser user = result.getUser().get();
        if (log.isDebugEnabled()) {
            try {
                String operatorUid = user.getUserId();
                String subjectType = user.getSubjectType();
                log.debug("auth result=AUTHENTICATED source=gateway method={} path={} operatorUid={} subjectType={} traceId={}",
                        exchange.getRequest().getMethod(), exchange.getRequest().getURI().getRawPath(),
                        operatorUid, subjectType, exchange.getAttribute(TraceHeaders.TRACE_ID_KEY));
            } catch (RuntimeException ignored) {
                // Authentication must not depend on logging.
            }
        }
        for (Map.Entry<String, String> entry : headerCodec.encode(user, false, user.getSubjectType()).entrySet()) {
            requestBuilder.headers(headers -> headers.set(entry.getKey(), entry.getValue()));
        }
        return chain.filter(exchange.mutate().request(requestBuilder.build()).build());
    }

    private boolean isIdentityHeader(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return identityHeaders.contains(lower)
                || lower.startsWith("x-") && lower.endsWith("-login-context");
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }

    private static Map<String, String> queryParams(ServerWebExchange exchange) {
        return exchange.getRequest().getQueryParams().entrySet().stream()
                .filter(entry -> entry.getValue() != null && !entry.getValue().isEmpty())
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> entry.getValue().stream()
                                .filter(value -> value != null && !value.isBlank())
                                .findFirst()
                                .orElse(""),
                        (left, right) -> left));
    }
}
