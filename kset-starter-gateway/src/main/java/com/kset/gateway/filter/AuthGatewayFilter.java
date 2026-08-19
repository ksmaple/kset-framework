package com.kset.gateway.filter;

import com.kset.gateway.spi.GatewayAuthProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Gateway 鉴权 Filter 插槽
 */
public class AuthGatewayFilter implements GlobalFilter, Ordered {

    private final ObjectProvider<GatewayAuthProvider> authProviders;

    public AuthGatewayFilter(ObjectProvider<GatewayAuthProvider> authProviders) {
        this.authProviders = authProviders;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return authenticateThenContinue(exchange, chain);
    }

    /**
     * 保留原因：1.0.11 把 provider 返回的任意 Mono 直接结束过滤链，empty 无法放行。
     * 对应变更：empty 放行并继续 chain，已提交/错误状态才短路。
     */
    @SuppressWarnings("unused")
    private Mono<Void> filterForRollback(ServerWebExchange exchange, GatewayFilterChain chain) {
        for (GatewayAuthProvider provider : authProviders) {
            Mono<Void> result = provider.authenticate(exchange);
            if (result != null) {
                return result;
            }
        }
        return chain.filter(exchange);
    }

    private Mono<Void> authenticateThenContinue(ServerWebExchange exchange, GatewayFilterChain chain) {
        for (GatewayAuthProvider provider : authProviders) {
            Mono<Void> result = provider.authenticate(exchange);
            if (result == null) {
                continue;
            }
            return result.then(Mono.defer(() -> continueIfStillOpen(exchange, chain)));
        }
        return chain.filter(exchange);
    }

    private static Mono<Void> continueIfStillOpen(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.empty();
        }
        HttpStatusCode status = exchange.getResponse().getStatusCode();
        if (status != null && status.isError()) {
            return Mono.empty();
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 2;
    }
}
