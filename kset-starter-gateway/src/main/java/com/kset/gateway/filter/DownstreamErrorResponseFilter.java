package com.kset.gateway.filter;

import com.kset.gateway.handler.GatewayErrorResponse;
import org.reactivestreams.Publisher;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.CLIENT_RESPONSE_ATTR;

/** Sanitizes HTTP 5xx responses received from a routed downstream service. */
public class DownstreamErrorResponseFilter implements GlobalFilter, Ordered {

    private final String traceHeader;

    public DownstreamErrorResponseFilter(String traceHeader) {
        this.traceHeader = traceHeader;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpResponseDecorator response = new ServerHttpResponseDecorator(exchange.getResponse()) {
            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                if (!shouldSanitize()) {
                    return super.writeWith(body);
                }
                return Flux.from(body).doOnNext(buffer -> DataBufferUtils.release(buffer))
                        .then(Mono.defer(this::writeSafeBody));
            }

            @Override
            public Mono<Void> writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>> body) {
                return writeWith(Flux.from(body).concatMap(Flux::from));
            }

            @Override
            public Mono<Void> setComplete() {
                return shouldSanitize() ? writeSafeBody() : super.setComplete();
            }

            private boolean shouldSanitize() {
                HttpStatusCode status = getStatusCode();
                return exchange.getAttribute(CLIENT_RESPONSE_ATTR) != null
                        && status != null && status.is5xxServerError();
            }

            private Mono<Void> writeSafeBody() {
                HttpStatusCode status = getStatusCode();
                String traceId = GatewayErrorResponse.traceId(exchange, traceHeader);
                getHeaders().remove(HttpHeaders.CONTENT_LENGTH);
                getHeaders().remove(HttpHeaders.CONTENT_ENCODING);
                getHeaders().remove(HttpHeaders.TRANSFER_ENCODING);
                getHeaders().setContentType(MediaType.APPLICATION_JSON);
                getHeaders().set(traceHeader, traceId);
                DataBuffer safeBody = bufferFactory().wrap(
                        GatewayErrorResponse.body(status, traceId).getBytes(StandardCharsets.UTF_8));
                return super.writeWith(Mono.just(safeBody));
            }
        };
        return chain.filter(exchange.mutate().response(response).build());
    }

    @Override
    public int getOrder() {
        return -2;
    }
}
