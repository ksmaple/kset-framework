package com.kset.gateway.spi;

import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Gateway 鉴权 SPI。打开 {@code kset.cloud.gateway.auth-enabled=true} 后生效。
 *
 * <p>{@code null}：本 provider 不处理，试下一个；
 * {@code Mono.empty()}：放行并继续过滤链；
 * 已提交或错误状态的响应 Mono：拒绝并结束。
 */
public interface GatewayAuthProvider {

    /**
     * @return {@code null} 表示本 provider 不处理，试下一个；
     * {@code Mono.empty()} 表示放行并继续过滤链；
     * 已完成的响应 Mono（如 401）表示拒绝并结束
     */
    Mono<Void> authenticate(ServerWebExchange exchange);
}
