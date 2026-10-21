package com.kset.common.monitor.gateway;

import com.kset.common.monitor.GatewayTraceBinding;
import com.kset.common.monitor.Monitor;
import com.kset.common.monitor.TraceSnapshot;
import com.kset.common.monitor.MonitorScope;
import com.kset.common.monitor.facade.MonitorStatus;
import com.kset.common.monitor.facade.MonitorTransaction;
import com.kset.common.monitor.facade.MonitorTypes;
import com.kset.common.trace.TraceHeaders;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Gateway TraceId 透传（经 {@link Monitor} 门面）。
 */
public class TraceIdGatewayFilter implements GlobalFilter, Ordered {

    private final String traceHeader;

    public TraceIdGatewayFilter(String traceHeader) {
        this.traceHeader = traceHeader;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String traceHeader = this.traceHeader;
        ServerHttpRequest request = exchange.getRequest();
        String incomingTraceId = request.getHeaders().getFirst(traceHeader);
        GatewayTraceBinding binding = Monitor.resolveGatewayTrace(incomingTraceId, traceHeader);
        String traceId = firstNonBlank(binding.getTraceId(), Monitor.generateTraceId());
        String spanId = firstNonBlank(binding.getSpanId(), Monitor.generateSpanId());
        String spanHeader = firstNonBlank(binding.getSpanIdHeaderName(), TraceHeaders.SPAN_ID_HEADER);
        exchange.getAttributes().put(TraceHeaders.TRACE_ID_KEY, traceId);
        exchange.getAttributes().put(TraceHeaders.TRACE_HEADER_KEY, traceHeader);
        exchange.getResponse().getHeaders().set(traceHeader, traceId);

        ServerHttpRequest mutated = request.mutate()
                .header(firstNonBlank(binding.getTraceHeaderName(), traceHeader), traceId)
                .header(spanHeader, spanId)
                .build();

        final String txName = request.getMethod() + " " + request.getURI().getRawPath();
        return Mono.defer(() -> {
            long startedNanos = System.nanoTime();
            AtomicReference<Throwable> failure = new AtomicReference<>();
            return Mono.defer(() -> chain.filter(exchange.mutate().request(mutated).build()))
                    .contextWrite(ctx -> (Context) Monitor.putReactorContext(ctx, traceId, null))
                    .doOnError(failure::set)
                    .doFinally(signalType -> {
                        String grayTag = exchange.getAttribute(TraceHeaders.GRAY_TAG_KEY);
                        try (MonitorScope ignored = Monitor.openScope(new TraceSnapshot(traceId, spanId, grayTag));
                             MonitorTransaction tx = Monitor.newTransaction(MonitorTypes.URL, txName, startedNanos)) {
                            tx.addData("component", "gateway");
                            tx.addData("method", request.getMethod().name());
                            tx.addData("uri", request.getURI().getRawPath());
                            tx.addData("signal", signalType.name());
                            HttpStatusCode status = exchange.getResponse().getStatusCode();
                            tx.addData("status", String.valueOf(status));
                            Throwable error = failure.get();
                            if (error != null) {
                                tx.setStatus(error);
                                tx.addData("errorType", error.getClass().getSimpleName());
                                Monitor.logError(error, txName);
                            } else if (signalType == reactor.core.publisher.SignalType.CANCEL
                                    || (status != null && status.isError())
                                    || Boolean.TRUE.equals(exchange.getAttribute(TraceHeaders.HTTP_FAILURE_ATTRIBUTE))) {
                                tx.setStatus(MonitorStatus.FAIL);
                            } else {
                                tx.setStatus(MonitorStatus.SUCCESS);
                            }
                        }
                    });
        });
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }
}
