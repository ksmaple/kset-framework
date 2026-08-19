package com.kset.gateway.auth;

import com.kset.cloud.config.KsetCloudProperties;
import com.kset.gateway.spi.GatewayAuthProvider;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;

/**
 * 共享密钥 Header Token 鉴权。
 *
 * <p>必须配置 {@code kset.cloud.gateway.auth-token}，请求头与配置值一致才放行。
 * 未配置或值不匹配返回 401。生产请实现 {@link com.kset.gateway.spi.GatewayAuthProvider} 换成 JWT/OAuth2。
 * 用法见 {@code docs/usage/gateway.md}。
 */
public class HeaderTokenGatewayAuthProvider implements GatewayAuthProvider {

    private final KsetCloudProperties properties;

    public HeaderTokenGatewayAuthProvider(KsetCloudProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @Override
    public Mono<Void> authenticate(ServerWebExchange exchange) {
        return authenticateConfiguredToken(exchange);
    }

    /**
     * 保留原因：请求头任意非空即放行，打开 auth-enabled 后形同虚设。
     */
    @SuppressWarnings("unused")
    private Mono<Void> authenticateForRollback(ServerWebExchange exchange) {
        String header = properties.getGateway().getAuthTokenHeader();
        String token = exchange.getRequest().getHeaders().getFirst(header);
        if (StringUtils.hasText(token)) {
            return Mono.empty();
        }
        return unauthorized(exchange);
    }

    private Mono<Void> authenticateConfiguredToken(ServerWebExchange exchange) {
        String expected = properties.getGateway().getAuthToken();
        if (!StringUtils.hasText(expected)) {
            return unauthorized(exchange);
        }
        String header = properties.getGateway().getAuthTokenHeader();
        String token = exchange.getRequest().getHeaders().getFirst(header);
        if (tokenMatches(expected, token)) {
            return Mono.empty();
        }
        return unauthorized(exchange);
    }

    private static boolean tokenMatches(String expected, String actual) {
        if (actual == null) {
            return false;
        }
        byte[] left = expected.getBytes(StandardCharsets.UTF_8);
        byte[] right = actual.getBytes(StandardCharsets.UTF_8);
        if (left.length != right.length) {
            return false;
        }
        return MessageDigest.isEqual(left, right);
    }

    private static Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }
}
