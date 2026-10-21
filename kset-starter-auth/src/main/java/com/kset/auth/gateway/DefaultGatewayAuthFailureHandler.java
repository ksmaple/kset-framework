package com.kset.auth.gateway;

import com.kset.common.trace.TraceHeaders;
import com.kset.common.utils.JsonUtil;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public class DefaultGatewayAuthFailureHandler implements GatewayAuthFailureHandler {

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, int code, String message) {
        exchange.getAttributes().put(TraceHeaders.HTTP_FAILURE_ATTRIBUTE, true);
        exchange.getResponse().setStatusCode(HttpStatus.OK);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);
        body.put("data", null);
        String traceId = exchange.getAttribute(TraceHeaders.TRACE_ID_KEY);
        body.put("traceId", traceId);
        if (traceId != null && !traceId.isBlank()) {
            exchange.getResponse().getHeaders().set(
                    exchange.getAttributeOrDefault(TraceHeaders.TRACE_HEADER_KEY, TraceHeaders.TRACE_ID_HEADER), traceId);
        }
        DataBuffer buffer = exchange.getResponse().bufferFactory()
                .wrap(JsonUtil.toJson(body).getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

}
