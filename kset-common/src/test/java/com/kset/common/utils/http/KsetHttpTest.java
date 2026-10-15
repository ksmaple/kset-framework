package com.kset.common.utils.http;

import com.kset.common.monitor.Monitor;
import com.kset.common.monitor.backend.MonitorBackend;
import com.kset.common.monitor.facade.CompletedTransaction;
import com.kset.common.monitor.facade.MonitorEvent;
import com.kset.common.monitor.facade.MonitorFacade;
import com.kset.common.monitor.facade.MonitorMetric;
import com.kset.common.monitor.facade.MonitorTypes;
import com.kset.common.monitor.internal.DefaultMonitorFacade;
import com.kset.common.monitor.reporter.NoOpMetricAggregator;
import com.kset.common.monitor.sampler.RateSampler;
import com.kset.common.trace.TraceHeaders;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class KsetHttpTest {

    @AfterEach
    void clearTrace() {
        Monitor.clear();
    }

    @Test
    void outboundTraceDoesNotPolluteCallerThread() throws Exception {
        AtomicReference<String> seenTrace = new AtomicReference<>();
        HttpServer server = startServer(seenTrace);
        try {
            Monitor.clear();
            HttpRequest request = HttpRequest.newBuilder(uri(server, "/ping"))
                    .version(HttpClient.Version.HTTP_1_1)
                    .GET()
                    .build();

            String body = KsetHttp.build().execute(request);

            assertThat(body).isEqualTo("ok");
            assertThat(seenTrace.get()).isNotBlank();
            assertThat(Monitor.currentTraceId()).isEmpty();
            assertThat(Monitor.currentSpanId()).isEmpty();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void keepsExistingTraceOnCallerThread() throws Exception {
        AtomicReference<String> seenTrace = new AtomicReference<>();
        HttpServer server = startServer(seenTrace);
        try {
            Monitor.setTraceId("trace-keep");
            Monitor.setSpanId("span-keep-123456");
            HttpRequest request = HttpRequest.newBuilder(uri(server, "/ping"))
                    .version(HttpClient.Version.HTTP_1_1)
                    .GET()
                    .build();

            KsetHttp.build().execute(request);

            assertThat(seenTrace.get()).isEqualTo("trace-keep");
            assertThat(Monitor.currentTraceId()).contains("trace-keep");
            assertThat(Monitor.currentSpanId()).contains("span-keep-123456");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void replacesCallerTraceHeadersAndKeepsExpectContinue() throws Exception {
        AtomicReference<List<String>> seenTrace = new AtomicReference<>();
        AtomicReference<String> seenExpect = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ping", exchange -> {
            seenTrace.set(exchange.getRequestHeaders().get(TraceHeaders.TRACE_ID_HEADER));
            seenExpect.set(exchange.getRequestHeaders().getFirst("Expect"));
            exchange.getRequestBody().readAllBytes();
            byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            Monitor.setTraceId("current-trace");
            HttpRequest request = HttpRequest.newBuilder(uri(server, "/ping"))
                    .version(HttpClient.Version.HTTP_1_1)
                    .header(TraceHeaders.TRACE_ID_HEADER, "stale-trace")
                    .expectContinue(true)
                    .POST(HttpRequest.BodyPublishers.ofString("body"))
                    .build();

            assertThat(KsetHttp.build().execute(request)).isEqualTo("ok");
            assertThat(seenTrace.get()).containsExactly("current-trace");
            assertThat(seenExpect.get()).isEqualToIgnoringCase("100-Continue");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void asyncTransactionIncludesNetworkWait() throws Exception {
        MonitorFacade original = Monitor.facade();
        AtomicReference<CompletedTransaction> completed = new AtomicReference<>();
        CountDownLatch received = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ping", exchange -> {
            received.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
                byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        try {
            Monitor.install(new DefaultMonitorFacade(new MonitorBackend() {
                @Override
                public boolean isEnabled() { return true; }

                @Override
                public void completeTransaction(CompletedTransaction transaction) { completed.set(transaction); }

                @Override
                public void logEvent(MonitorEvent event) { }

                @Override
                public void logMetric(MonitorMetric metric) { }

                @Override
                public void logError(Throwable error, String message) { }
            }, new RateSampler(1.0), new NoOpMetricAggregator()));
            HttpRequest request = HttpRequest.newBuilder(uri(server, "/ping"))
                    .version(HttpClient.Version.HTTP_1_1).GET().build();
            CompletableFuture<?> result = KsetHttp.build().enqueue(request);

            assertThat(received.await(5, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(100);
            release.countDown();
            result.get(5, TimeUnit.SECONDS);

            assertThat(completed.get()).isNotNull();
            assertThat(completed.get().type()).isEqualTo(MonitorTypes.HTTP_CLIENT);
            assertThat(completed.get().durationMs()).isGreaterThanOrEqualTo(100);
        } finally {
            release.countDown();
            server.stop(0);
            Monitor.clear();
            Monitor.install(original);
        }
    }

    private static HttpServer startServer(AtomicReference<String> seenTrace) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ping", exchange -> {
            seenTrace.set(exchange.getRequestHeaders().getFirst(TraceHeaders.TRACE_ID_HEADER));
            byte[] body = "ok".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return server;
    }

    private static URI uri(HttpServer server, String path) {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
    }
}
