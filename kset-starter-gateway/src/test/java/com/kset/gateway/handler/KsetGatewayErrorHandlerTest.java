package com.kset.gateway.handler;

import com.kset.common.trace.TraceHeaders;
import com.kset.common.utils.JsonUtil;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;

import java.net.ConnectException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class KsetGatewayErrorHandlerTest {

    private final KsetGatewayErrorHandler handler = new KsetGatewayErrorHandler();

    @Test
    void unavailableServiceKeepsTraceIdAndSafeMessage() {
        MockServerWebExchange exchange = exchange();
        exchange.getAttributes().put(TraceHeaders.TRACE_ID_KEY, "trace-123");

        handler.handle(exchange, new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "internal instance details")).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(exchange.getResponse().getHeaders().getFirst(TraceHeaders.TRACE_ID_HEADER))
                .isEqualTo("trace-123");
        var body = JsonUtil.readTree(exchange.getResponse().getBodyAsString().block());
        assertThat(body.get("code").asInt()).isEqualTo(503);
        assertThat(body.get("message").asText()).isEqualTo("服务暂时不可用，请稍后重试");
        assertThat(body.get("traceId").asText()).isEqualTo("trace-123");
        assertThat(body.get("data").isNull()).isTrue();
    }

    @Test
    void networkAndTimeoutFailuresHaveDifferentStatuses() {
        MockServerWebExchange networkExchange = exchange();
        handler.handle(networkExchange, new IllegalStateException(new ConnectException())).block();
        assertThat(networkExchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

        MockServerWebExchange timeoutExchange = exchange();
        handler.handle(timeoutExchange, new IllegalStateException(new TimeoutException())).block();
        assertThat(timeoutExchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
    }

    @Test
    void responseStatusIsPreservedAndUnknownFailureIsSanitized() {
        MockServerWebExchange missingRoute = exchange();
        handler.handle(missingRoute, new ResponseStatusException(HttpStatus.NOT_FOUND)).block();
        assertThat(missingRoute.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        MockServerWebExchange unknown = exchange();
        handler.handle(unknown, new IllegalStateException("internal detail")).block();
        assertThat(unknown.getResponse().getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        var body = JsonUtil.readTree(unknown.getResponse().getBodyAsString().block());
        assertThat(body.get("code").asInt()).isEqualTo(-1);
        assertThat(body.get("message").asText()).isEqualTo("系统异常，请稍后重试");
        assertThat(body.get("traceId").asText()).isNotBlank();
    }

    @Test
    void preservesIncomingTraceOnUnroutedErrorWithConfiguredHeader() {
        KsetGatewayErrorHandler customHeaderHandler = new KsetGatewayErrorHandler("X-Request-Trace");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/missing").header("X-Request-Trace", "incoming-trace"));

        customHeaderHandler.handle(exchange, new ResponseStatusException(HttpStatus.NOT_FOUND)).block();

        assertThat(exchange.getResponse().getHeaders().getFirst("X-Request-Trace"))
                .isEqualTo("incoming-trace");
        assertThat(JsonUtil.readTree(exchange.getResponse().getBodyAsString().block())
                .get("traceId").asText()).isEqualTo("incoming-trace");
    }

    private static MockServerWebExchange exchange() {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/api/orders").build());
    }
}
