package com.kset.gateway.handler;

import com.kset.common.monitor.Monitor;
import com.kset.common.trace.TraceHeaders;
import com.kset.common.utils.JsonUtil;
import org.springframework.core.env.Environment;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class GatewayErrorResponse {

    private GatewayErrorResponse() {
    }

    public static String configuredTraceHeader(Environment environment) {
        String own = environment.getProperty("kset.monitor.gateway.trace-header");
        if (own != null && !own.isBlank()) {
            return own.trim();
        }
        String legacy = environment.getProperty("kset.cloud.gateway.trace-header");
        return legacy != null && !legacy.isBlank() ? legacy.trim() : TraceHeaders.TRACE_ID_HEADER;
    }

    public static String traceId(ServerWebExchange exchange, String traceHeader) {
        String bound = exchange.getAttribute(TraceHeaders.TRACE_ID_KEY);
        if (bound != null && !bound.isBlank()) {
            return bound;
        }
        String incoming = exchange.getRequest().getHeaders().getFirst(traceHeader);
        String traceId = incoming != null && !incoming.isBlank() ? incoming : Monitor.generateTraceId();
        exchange.getAttributes().put(TraceHeaders.TRACE_ID_KEY, traceId);
        return traceId;
    }

    public static String body(HttpStatusCode status, String traceId) {
        String message = switch (status.value()) {
            case 401 -> "未登录";
            case 403 -> "无权限";
            case 404 -> "请求资源不存在";
            case 503 -> "服务暂时不可用，请稍后重试";
            case 504 -> "服务响应超时，请稍后重试";
            case 500 -> "系统异常，请稍后重试";
            default -> "请求处理失败";
        };
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("code", status.value() == 500 ? -1 : status.value());
        response.put("message", message);
        response.put("data", null);
        response.put("traceId", traceId);
        return JsonUtil.toJson(response);
    }

    public static Mono<Void> write(ServerWebExchange exchange, HttpStatusCode status, String traceHeader) {
        String traceId = traceId(exchange, traceHeader);
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().set(traceHeader, traceId);
        DataBuffer buffer = exchange.getResponse().bufferFactory()
                .wrap(body(status, traceId).getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
