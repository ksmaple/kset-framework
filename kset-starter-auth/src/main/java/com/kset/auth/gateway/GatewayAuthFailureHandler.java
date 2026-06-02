package com.kset.auth.gateway;

import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public interface GatewayAuthFailureHandler {

    Mono<Void> handle(ServerWebExchange exchange, int code, String message);
}
