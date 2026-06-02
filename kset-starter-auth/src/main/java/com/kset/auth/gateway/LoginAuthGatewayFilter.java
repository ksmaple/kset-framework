package com.kset.auth.gateway;

import com.kset.auth.config.KsetAuthProperties;
import com.kset.auth.core.AuthRequest;
import com.kset.auth.core.AuthResult;
import com.kset.auth.core.AuthRuleResolver;
import com.kset.auth.core.LoginAuthService;
import com.kset.auth.spi.LoginUserHeaderCodec;
import com.kset.common.auth.LoginUser;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

public class LoginAuthGatewayFilter implements GlobalFilter, Ordered {

    private final KsetAuthProperties properties;
    private final LoginAuthService authService;
    private final LoginUserHeaderCodec headerCodec;
    private final GatewayAuthFailureHandler failureHandler;

    public LoginAuthGatewayFilter(KsetAuthProperties properties,
                                  LoginAuthService authService,
                                  LoginUserHeaderCodec headerCodec,
                                  GatewayAuthFailureHandler failureHandler) {
        this.properties = properties;
        this.authService = authService;
        this.headerCodec = headerCodec;
        this.failureHandler = failureHandler;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!properties.getGateway().isEnabled()) {
            return chain.filter(exchange);
        }
        AuthResult result = authService.authenticate(new AuthRequest(
                exchange.getRequest().getURI().getPath(),
                name -> exchange.getRequest().getHeaders().getFirst(name),
                AuthRuleResolver.SOURCE_GATEWAY));
        if (result.isPermitAll()) {
            return chain.filter(exchange);
        }
        if (!result.isAuthenticated() || result.getUser().isEmpty()) {
            return failureHandler.handle(exchange, result.getCode(), result.getMessage());
        }
        LoginUser user = result.getUser().get();
        ServerHttpRequest.Builder requestBuilder = exchange.getRequest().mutate();
        for (Map.Entry<String, String> entry : headerCodec.encode(user, false, user.getSubjectType()).entrySet()) {
            requestBuilder.header(entry.getKey(), entry.getValue());
        }
        return chain.filter(exchange.mutate().request(requestBuilder.build()).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
