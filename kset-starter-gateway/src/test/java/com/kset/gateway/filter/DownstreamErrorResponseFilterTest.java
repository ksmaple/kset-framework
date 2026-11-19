package com.kset.gateway.filter;

import com.kset.common.utils.JsonUtil;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.CLIENT_RESPONSE_ATTR;

class DownstreamErrorResponseFilterTest {

    @Test
    void replacesDownstreamServiceFailureBody() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/orders"));
        exchange.getAttributes().put(CLIENT_RESPONSE_ATTR, new Object());
        DownstreamErrorResponseFilter filter = new DownstreamErrorResponseFilter("X-Request-Trace");

        filter.filter(exchange, routed -> {
            routed.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
            routed.getResponse().getHeaders().setContentLength(100);
            return routed.getResponse().writeWith(Mono.just(routed.getResponse().bufferFactory()
                    .wrap("internal host detail".getBytes(StandardCharsets.UTF_8))));
        }).block();

        var body = JsonUtil.readTree(exchange.getResponse().getBodyAsString().block());
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(exchange.getResponse().getHeaders().getContentLength()).isEqualTo(-1);
        assertThat(body.get("code").asInt()).isEqualTo(503);
        assertThat(body.get("message").asText()).isEqualTo("服务暂时不可用，请稍后重试");
        assertThat(body.get("traceId").asText()).isNotBlank();
        assertThat(body.toString()).doesNotContain("internal host detail");
    }

    @Test
    void doesNotRewriteLocalErrorWithoutDownstreamResponse() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/orders"));
        DownstreamErrorResponseFilter filter = new DownstreamErrorResponseFilter("X-Trace-Id");

        filter.filter(exchange, routed -> {
            routed.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return routed.getResponse().writeWith(Mono.just(routed.getResponse().bufferFactory()
                    .wrap("custom auth response".getBytes(StandardCharsets.UTF_8))));
        }).block();

        assertThat(exchange.getResponse().getBodyAsString().block()).isEqualTo("custom auth response");
    }

    @Test
    void replacesFlushedDownstreamFailureBody() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/events"));
        exchange.getAttributes().put(CLIENT_RESPONSE_ATTR, new Object());
        DownstreamErrorResponseFilter filter = new DownstreamErrorResponseFilter("X-Request-Trace");

        filter.filter(exchange, routed -> {
            routed.getResponse().setStatusCode(HttpStatus.BAD_GATEWAY);
            return routed.getResponse().writeAndFlushWith(Flux.just(
                    Mono.just(routed.getResponse().bufferFactory().wrap("private".getBytes(StandardCharsets.UTF_8))),
                    Mono.just(routed.getResponse().bufferFactory().wrap(" detail".getBytes(StandardCharsets.UTF_8)))));
        }).block();

        String body = exchange.getResponse().getBodyAsString().block();
        assertThat(body).doesNotContain("private detail");
        assertThat(JsonUtil.readTree(body).get("code").asInt()).isEqualTo(502);
    }
}
