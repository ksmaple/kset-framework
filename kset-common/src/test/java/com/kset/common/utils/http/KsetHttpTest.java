package com.kset.common.utils.http;

import com.kset.common.monitor.Monitor;
import com.kset.common.trace.TraceHeaders;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
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
