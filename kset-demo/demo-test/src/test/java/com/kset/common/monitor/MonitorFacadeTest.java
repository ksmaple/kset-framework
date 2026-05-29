package com.kset.common.monitor;

import com.kset.common.monitor.internal.NoOpMonitorFacade;
import com.kset.common.monitor.facade.MonitorFacade;
import com.kset.common.monitor.facade.MetricKind;
import com.kset.common.monitor.facade.MonitorStatus;
import com.kset.common.monitor.facade.MonitorTransaction;
import com.kset.common.monitor.facade.MonitorTypes;
import com.kset.common.monitor.internal.DefaultMonitorFacade;
import com.kset.common.monitor.backend.LogBackend;
import com.kset.common.monitor.reporter.DefaultMetricAggregator;
import com.kset.common.monitor.reporter.NoOpMetricAggregator;
import com.kset.common.monitor.reporter.SyncAsyncReporter;
import com.kset.common.monitor.sampler.RateSampler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MonitorFacadeTest {

    @AfterEach
    void resetFacade() {
        Monitor.install(new NoOpMonitorFacade());
    }

    @Test
    void builtinDefaultUsesLogBackend() {
        Monitor.install(new DefaultMonitorFacade(
                new LogBackend(500),
                new RateSampler(1.0),
                new SyncAsyncReporter(),
                new NoOpMetricAggregator(),
                false));
        assertDoesNotThrow(() -> {
            try (MonitorTransaction tx = Monitor.newTransaction(MonitorTypes.BIZ, "builtin")) {
                tx.setStatus(MonitorStatus.SUCCESS);
            }
        });
    }

    @Test
    void noOpTransactionDoesNotThrow() {
        assertDoesNotThrow(() -> {
            try (MonitorTransaction tx = Monitor.newTransaction(MonitorTypes.BIZ, "test")) {
                tx.setStatus(MonitorStatus.SUCCESS);
            }
        });
    }

    @Test
    void defaultFacadeNestedTransactionCompletes() {
        LogBackend backend = new LogBackend(500);
        DefaultMonitorFacade facade = new DefaultMonitorFacade(
                backend,
                new RateSampler(1.0),
                new SyncAsyncReporter(),
                new DefaultMetricAggregator(),
                false);
        Monitor.install(facade);
        facade.setTraceId("trace-1");
        try (MonitorTransaction outer = Monitor.newTransaction(MonitorTypes.URL, "/order")) {
            try (MonitorTransaction inner = Monitor.newTransaction(MonitorTypes.SQL, "selectOrder")) {
                inner.setStatus(MonitorStatus.SUCCESS);
            }
            outer.setStatus(MonitorStatus.SUCCESS);
        }
        assertTrue(Monitor.currentTraceId().isPresent());
    }

    @Test
    void samplerCanDropTransactions() {
        LogBackend backend = new LogBackend(500);
        DefaultMonitorFacade facade = new DefaultMonitorFacade(
                backend,
                new RateSampler(0.0),
                new SyncAsyncReporter(),
                new DefaultMetricAggregator(),
                false);
        Monitor.install(facade);
        try (MonitorTransaction tx = Monitor.newTransaction(MonitorTypes.BIZ, "ignored")) {
            tx.setStatus(MonitorStatus.SUCCESS);
        }
        assertDoesNotThrow(() -> Monitor.logMetric("demo.count", 1, MetricKind.COUNT));
    }

    @Test
    void facadeFailureDoesNotAffectBusinessFlow() {
        Monitor.install(new FailingMonitorFacade());
        AtomicBoolean executed = new AtomicBoolean(false);

        assertDoesNotThrow(() -> Monitor.runInTransaction(MonitorTypes.BIZ, "safe", () -> executed.set(true)));
        assertTrue(executed.get());
        assertDoesNotThrow(() -> Monitor.logEvent(MonitorTypes.BIZ, "event", MonitorStatus.SUCCESS, "ok"));
        assertDoesNotThrow(() -> Monitor.currentTraceId());
    }

    @Test
    void facadeFailureKeepsLocalTraceContextFallback() {
        Monitor.install(new FailingMonitorFacade());

        HttpTraceBinding binding = Monitor.bindHttpIncoming("trace-fallback");

        assertTrue(Monitor.currentTraceId().isPresent());
        assertTrue(Monitor.currentTraceId().get().equals(binding.getTraceId()));
    }

    static class FailingMonitorFacade implements MonitorFacade {

        @Override
        public Optional<String> currentTraceId() {
            throw failure();
        }

        @Override
        public Optional<String> currentSpanId() {
            throw failure();
        }

        @Override
        public Optional<String> currentGrayTag() {
            throw failure();
        }

        @Override
        public String generateTraceId() {
            throw failure();
        }

        @Override
        public String generateSpanId() {
            throw failure();
        }

        @Override
        public HttpTraceBinding bindHttpIncoming(String incomingTraceId) {
            throw failure();
        }

        @Override
        public void bindHttpGrayTag(String incomingGrayTag, String defaultGray) {
            throw failure();
        }

        @Override
        public void clearHttpGrayTag() {
            throw failure();
        }

        @Override
        public void bindDubboConsumer(DubboAttachmentAccessor attachments, String defaultGray) {
            throw failure();
        }

        @Override
        public void bindDubboProvider(DubboAttachmentAccessor attachments, String defaultGray) {
            throw failure();
        }

        @Override
        public GatewayTraceBinding resolveGatewayTrace(String incomingTraceId, String traceHeaderName) {
            throw failure();
        }

        @Override
        public Object putReactorContext(Object context, String traceId, String grayTag) {
            throw failure();
        }

        @Override
        public Optional<String> getFromReactor(Object contextView, String key) {
            throw failure();
        }

        @Override
        public void setTraceId(String traceId) {
            throw failure();
        }

        @Override
        public void setSpanId(String spanId) {
            throw failure();
        }

        @Override
        public void setGrayTag(String grayTag) {
            throw failure();
        }

        @Override
        public void clear() {
            throw failure();
        }

        @Override
        public TraceSnapshot capture() {
            throw failure();
        }

        @Override
        public void restore(TraceSnapshot snapshot) {
            throw failure();
        }

        @Override
        public MonitorTransaction newTransaction(String type, String name) {
            throw failure();
        }

        @Override
        public void logEvent(String type, String name, MonitorStatus status, String data) {
            throw failure();
        }

        @Override
        public void logMetric(String name, long value, MetricKind kind) {
            throw failure();
        }

        @Override
        public void logError(Throwable throwable, String message) {
            throw failure();
        }

        private RuntimeException failure() {
            return new IllegalStateException("monitor failed");
        }
    }
}
