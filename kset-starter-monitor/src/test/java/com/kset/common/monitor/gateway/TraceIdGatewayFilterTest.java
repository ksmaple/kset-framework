package com.kset.common.monitor.gateway;

import com.kset.common.monitor.Monitor;
import com.kset.common.monitor.backend.MonitorBackend;
import com.kset.common.monitor.internal.DefaultMonitorFacade;
import com.kset.common.monitor.reporter.NoOpMetricAggregator;
import com.kset.common.monitor.sampler.RateSampler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.timeout;

class TraceIdGatewayFilterTest {

    @AfterEach
    void restoreMonitor() {
        Monitor.clear();
        Monitor.uninstall();
    }

    @Test
    void completesTransactionAfterThreadSwitchWithoutLeakingTrace() {
        MonitorBackend backend = mock(MonitorBackend.class);
        when(backend.isEnabled()).thenReturn(true);
        Monitor.install(new DefaultMonitorFacade(backend, new RateSampler(1.0), new NoOpMetricAggregator()));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/orders").header("X-Trace-Id", "trace-123"));

        new TraceIdGatewayFilter("X-Trace-Id")
                .filter(exchange, ignored -> Mono.delay(Duration.ofMillis(1)).then())
                .block();

        verify(backend, timeout(1000)).completeTransaction(
                argThat(tx -> tx.traceId().orElse("").equals("trace-123")));
        assertThat(Monitor.currentTraceId()).isEmpty();
    }
}
