package com.kset.cloud.gateway.auth;

import com.kset.cloud.config.KsetCloudProperties;
import com.kset.cloud.gateway.spi.GatewayAuthProvider;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 简单 Header Token 鉴权（生产环境请替换为 JWT/OAuth2 实现）。
 */
public class HeaderTokenGatewayAuthProvider implements GatewayAuthProvider {

    private final KsetCloudProperties properties;

    public HeaderTokenGatewayAuthProvider(KsetCloudProperties properties) {
        this.properties = properties;
    }

    @Override
    public Mono<Void> authenticate(ServerWebExchange exchange) {
        String header = properties.getGateway().getAuthTokenHeader();
        String token = exchange.getRequest().getHeaders().getFirst(header);
        if (StringUtils.hasText(token)) {
            return Mono.empty();
        }
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }
}
