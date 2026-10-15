package com.kset.common.monitor;

import com.kset.common.monitor.autoconfigure.KsetMonitorHttpClientAutoConfiguration;
import com.kset.common.monitor.backend.MonitorBackend;
import com.kset.common.monitor.facade.CompletedTransaction;
import com.kset.common.monitor.facade.MonitorEvent;
import com.kset.common.monitor.facade.MonitorFacade;
import com.kset.common.monitor.facade.MonitorMetric;
import com.kset.common.monitor.internal.DefaultMonitorFacade;
import com.kset.common.monitor.reporter.NoOpMetricAggregator;
import com.kset.common.monitor.sampler.RateSampler;
import com.kset.common.utils.http.KsetHttp;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class KsetMonitorHttpClientAutoConfigurationTest {

    @Test
    void globalAndClientSwitchesControlHttpTransactions() throws Exception {
        MonitorFacade original = Monitor.facade();
        AtomicInteger transactions = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ping", exchange -> {
            byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            Monitor.install(new DefaultMonitorFacade(new MonitorBackend() {
                @Override
                public boolean isEnabled() { return true; }

                @Override
                public void completeTransaction(CompletedTransaction transaction) { transactions.incrementAndGet(); }

                @Override
                public void logEvent(MonitorEvent event) { }

                @Override
                public void logMetric(MonitorMetric metric) { }

                @Override
                public void logError(Throwable error, String message) { }
            }, new RateSampler(1.0), new NoOpMetricAggregator()));
            HttpRequest request = HttpRequest.newBuilder(
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/ping"))
                    .version(HttpClient.Version.HTTP_1_1).GET().build();

            new KsetMonitorHttpClientAutoConfiguration(
                    new MockEnvironment().withProperty("kset.monitor.enabled", "false"));
            assertThat(KsetHttp.build().execute(request)).isEqualTo("ok");
            new KsetMonitorHttpClientAutoConfiguration(
                    new MockEnvironment().withProperty("kset.monitor.http-client.enabled", "false"));
            assertThat(KsetHttp.build().execute(request)).isEqualTo("ok");
            assertThat(transactions.get()).isZero();

            new KsetMonitorHttpClientAutoConfiguration(new MockEnvironment());
            assertThat(KsetHttp.build().execute(request)).isEqualTo("ok");
            assertThat(transactions.get()).isEqualTo(1);
        } finally {
            server.stop(0);
            Monitor.clear();
            Monitor.install(original);
            KsetHttp.setMonitorEnabled(true);
        }
    }
}
