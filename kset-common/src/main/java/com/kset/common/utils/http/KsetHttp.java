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

/** JDK 21 HTTP 客户端封装。 */
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
        HttpRequest tracedRequest = withTraceHeaders(request);
        return client.sendAsync(tracedRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .whenComplete((response, error) -> finish(tracedRequest, response, error));
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
        HttpRequest tracedRequest = withTraceHeaders(request);
        MonitorScope scope = new MonitorScope(tracedRequest);
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

    private void finish(HttpRequest request, HttpResponse<String> response, Throwable error) {
        try (MonitorScope scope = new MonitorScope(request)) {
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

    private static HttpRequest withTraceHeaders(HttpRequest request) {
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

    private static void addHeaders(HttpRequest.Builder builder, Map<String, String> headers) {
        headers.forEach((name, value) -> {
            if (value != null && !MANAGED_HEADERS.contains(name.toLowerCase())) {
                builder.header(name, value);
            }
        });
    }

    private static final class MonitorScope implements AutoCloseable {
        private final MonitorTransaction transaction;

        private MonitorScope(HttpRequest request) {
            if (!monitorEnabled) {
                this.transaction = null;
                return;
            }
            TraceSnapshot previous = Monitor.capture();
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
            if (transaction != null) {
                transaction.close();
            }
        }
    }
}
