package com.kset.common.utils.http;

import com.kset.common.monitor.Monitor;
import com.kset.common.monitor.TraceSnapshot;
import com.kset.common.monitor.facade.MonitorStatus;
import com.kset.common.monitor.facade.MonitorTransaction;
import com.kset.common.monitor.facade.MonitorTypes;
import com.kset.common.trace.TraceHeaders;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * JDK 21 HTTP 客户端封装。
 *
 * <p>出站自动带 Trace / 灰度头，不改调用线程的 Trace。监控 scope 包住整次发送。
 */
public final class KsetHttp {

    private static final KsetHttp INSTANCE = new KsetHttp();
    private static final Set<String> MANAGED_HEADERS = Set.of(
            "connection", "content-length", "expect", "host", "upgrade");
    private static volatile boolean monitorEnabled = true;

    private final HttpClient client;

    private KsetHttp() {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .version(HttpClient.Version.HTTP_2)
                .build();
    }

    public static KsetHttp build() {
        return INSTANCE;
    }

    public static void setMonitorEnabled(boolean enabled) {
        monitorEnabled = enabled;
    }

    public String execute(HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<String> response = send(request);
        return response.body();
    }

    public CompletableFuture<HttpResponse<String>> enqueue(HttpRequest request) {
        OutboundTrace trace = resolveOutboundTrace();
        HttpRequest tracedRequest = copyWithTraceHeaders(request, trace);
        return client.sendAsync(tracedRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .whenComplete((response, error) -> finish(tracedRequest, trace, response, error));
    }

    public String execute(String url, Map<String, String> headers, Map<String, String> formBody)
            throws IOException, InterruptedException {
        return execute(formRequest(url, headers, formBody));
    }

    public CompletableFuture<HttpResponse<String>> enqueue(
            String url, Map<String, String> headers, Map<String, String> formBody) {
        return enqueue(formRequest(url, headers, formBody));
    }

    private HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
        OutboundTrace trace = resolveOutboundTrace();
        HttpRequest tracedRequest = copyWithTraceHeaders(request, trace);
        MonitorScope scope = MonitorScope.open(tracedRequest, trace);
        try {
            HttpResponse<String> response = client.send(
                    tracedRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            scope.success(response);
            return response;
        } catch (IOException | InterruptedException e) {
            scope.failure(e);
            throw e;
        } finally {
            scope.close();
        }
    }

    private void finish(HttpRequest request, OutboundTrace trace, HttpResponse<String> response, Throwable error) {
        try (MonitorScope scope = MonitorScope.open(request, trace)) {
            if (error != null) {
                scope.failure(error);
            } else {
                scope.success(response);
            }
        }
    }

    private static HttpRequest formRequest(String url, Map<String, String> headers, Map<String, String> formBody) {
        String body = HttpConvertUtils.convertMapToHttpFormBody(formBody);
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (headers != null) {
            addHeaders(builder, headers);
        }
        return builder.build();
    }

    @SuppressWarnings("unused")
    private static HttpRequest withTraceHeaders(HttpRequest request) {
        return copyWithTraceHeaders(request, resolveOutboundTrace());
    }

    /**
     * 保留原因：无 inbound trace 时 setTraceId/setSpanId 污染调用线程。
     */
    @SuppressWarnings("unused")
    private static HttpRequest withTraceHeadersForRollback(HttpRequest request) {
        String traceId = Monitor.currentTraceId().orElseGet(() -> {
            String value = UUID.randomUUID().toString().replace("-", "");
            Monitor.setTraceId(value);
            return value;
        });
        String spanId = Monitor.currentSpanId().orElseGet(() -> {
            String value = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            Monitor.setSpanId(value);
            return value;
        });
        HttpRequest.Builder builder = HttpRequest.newBuilder(request.uri())
                .timeout(request.timeout().orElse(Duration.ofSeconds(10)))
                .version(request.version().orElse(HttpClient.Version.HTTP_2))
                .method(request.method(), request.bodyPublisher().orElse(HttpRequest.BodyPublishers.noBody()));
        request.headers().map().forEach((name, values) -> {
            if (!MANAGED_HEADERS.contains(name.toLowerCase())) {
                values.forEach(value -> builder.header(name, value));
            }
        });
        builder.header(TraceHeaders.TRACE_ID_HEADER, traceId)
                .header(TraceHeaders.SPAN_ID_HEADER, spanId);
        Monitor.currentGrayTag().ifPresent(value -> builder.header(TraceHeaders.GRAY_TAG_HEADER, value));
        return builder.build();
    }

    private static OutboundTrace resolveOutboundTrace() {
        String traceId = firstNonBlank(Monitor.currentTraceId().orElse(null), Monitor.generateTraceId());
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        String spanId = firstNonBlank(Monitor.currentSpanId().orElse(null), Monitor.generateSpanId());
        if (spanId == null || spanId.isBlank()) {
            spanId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }
        return new OutboundTrace(traceId, spanId, Monitor.currentGrayTag().orElse(null));
    }

    private static HttpRequest copyWithTraceHeaders(HttpRequest request, OutboundTrace trace) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(request.uri())
                .timeout(request.timeout().orElse(Duration.ofSeconds(10)))
                .version(request.version().orElse(HttpClient.Version.HTTP_2))
                .method(request.method(), request.bodyPublisher().orElse(HttpRequest.BodyPublishers.noBody()));
        request.headers().map().forEach((name, values) -> {
            if (!MANAGED_HEADERS.contains(name.toLowerCase())) {
                values.forEach(value -> builder.header(name, value));
            }
        });
        builder.header(TraceHeaders.TRACE_ID_HEADER, trace.traceId())
                .header(TraceHeaders.SPAN_ID_HEADER, trace.spanId());
        if (trace.grayTag() != null && !trace.grayTag().isBlank()) {
            builder.header(TraceHeaders.GRAY_TAG_HEADER, trace.grayTag());
        }
        return builder.build();
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    private static void addHeaders(HttpRequest.Builder builder, Map<String, String> headers) {
        headers.forEach((name, value) -> {
            if (value != null && !MANAGED_HEADERS.contains(name.toLowerCase())) {
                builder.header(name, value);
            }
        });
    }

    private record OutboundTrace(String traceId, String spanId, String grayTag) {
    }

    private static final class MonitorScope implements AutoCloseable {
        private final TraceSnapshot previous;
        private final MonitorTransaction transaction;

        private static MonitorScope open(HttpRequest request, OutboundTrace trace) {
            return new MonitorScope(request, trace);
        }

        private MonitorScope(HttpRequest request, OutboundTrace trace) {
            this.previous = Monitor.capture();
            Monitor.setTraceId(trace.traceId());
            Monitor.setSpanId(trace.spanId());
            if (trace.grayTag() != null && !trace.grayTag().isBlank()) {
                Monitor.setGrayTag(trace.grayTag());
            }
            if (!monitorEnabled) {
                this.transaction = null;
                return;
            }
            String name = request.method() + " " + request.uri().getPath();
            this.transaction = Monitor.newTransaction(MonitorTypes.HTTP_CLIENT, name);
            transaction.addData("component", "http-client");
            transaction.addData("method", request.method());
            transaction.addData("host", request.uri().getHost());
            transaction.addData("path", request.uri().getPath());
        }

        /**
         * 保留原因：newTransaction 后立刻 restore，出站 header 与监控事务对不上。
         */
        @SuppressWarnings("unused")
        private MonitorScope(HttpRequest request) {
            if (!monitorEnabled) {
                this.previous = Monitor.capture();
                this.transaction = null;
                return;
            }
            this.previous = Monitor.capture();
            String name = request.method() + " " + request.uri().getPath();
            this.transaction = Monitor.newTransaction(MonitorTypes.HTTP_CLIENT, name);
            transaction.addData("component", "http-client");
            transaction.addData("method", request.method());
            transaction.addData("host", request.uri().getHost());
            transaction.addData("path", request.uri().getPath());
            Monitor.restore(previous);
        }

        private void success(HttpResponse<String> response) {
            if (transaction == null) {
                return;
            }
            transaction.addData("status", String.valueOf(response.statusCode()));
            transaction.setStatus(MonitorStatus.SUCCESS);
        }

        private void failure(Throwable error) {
            if (transaction == null) {
                return;
            }
            transaction.setStatus(error);
            Monitor.logError(error, transaction.toString());
        }

        @Override
        public void close() {
            try {
                if (transaction != null) {
                    transaction.close();
                }
            } finally {
                Monitor.restore(previous);
            }
        }
    }
}
