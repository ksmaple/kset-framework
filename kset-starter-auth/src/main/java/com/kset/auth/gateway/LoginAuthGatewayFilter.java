package com.kset.auth.gateway;

import com.kset.auth.config.KsetAuthProperties;
import com.kset.auth.core.AuthRequest;
import com.kset.auth.core.AuthResult;
import com.kset.auth.core.AuthRuleResolver;
import com.kset.auth.core.LoginAuthService;
import com.kset.auth.spi.LoginUserHeaderCodec;
import com.kset.common.auth.AuthHeaders;
import com.kset.common.auth.LoginUser;
import com.kset.common.trace.TraceHeaders;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

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
        AuthResult result = authService.authenticate(new AuthRequest(
                exchange.getRequest().getURI().getPath(),
                name -> exchange.getRequest().getHeaders().getFirst(name),
                AuthRuleResolver.SOURCE_GATEWAY,
                exchange.getRequest().getMethod() != null ? exchange.getRequest().getMethod().name() : null,
                queryParams(exchange)));
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
