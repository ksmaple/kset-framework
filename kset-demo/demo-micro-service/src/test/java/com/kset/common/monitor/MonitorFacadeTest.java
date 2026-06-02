package com.kset.common.monitor;

import com.kset.common.context.KsetContext;
import com.kset.common.context.KsetContextKeys;
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
import com.kset.common.monitor.sampler.RateSampler;
import com.kset.common.trace.TraceHeaders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MonitorFacadeTest {

    @AfterEach
    void resetFacade() {
        Monitor.clear();
        Monitor.install(new NoOpMonitorFacade());
    }

    @Test
    void builtinDefaultUsesLogBackend() {
        Monitor.install(new DefaultMonitorFacade(
                new LogBackend(500),
                new RateSampler(1.0),
                new NoOpMetricAggregator()));
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
                new DefaultMetricAggregator());
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
                new DefaultMetricAggregator());
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

        assertEquals("trace-fallback", binding.getTraceId());
        assertEquals(binding.getTraceId(), Monitor.currentTraceId().orElseThrow());
        assertEquals(binding.getTraceId(), KsetContext.get(KsetContextKeys.TRACE_ID).orElseThrow());
        assertEquals(binding.getSpanId(), KsetContext.get(KsetContextKeys.SPAN_ID).orElseThrow());
    }

    @Test
    void facadeFailureKeepsDubboProviderContextFallback() {
        Monitor.install(new FailingMonitorFacade());
        Map<String, String> attachments = new HashMap<>();
        attachments.put(TraceHeaders.TRACE_ID_KEY, "trace-dubbo");
        attachments.put(TraceHeaders.GRAY_TAG_KEY, "gray-dubbo");

        Monitor.bindDubboProvider(new MapDubboAttachmentAccessor(attachments), "gray-default");

        assertEquals("trace-dubbo", Monitor.currentTraceId().orElseThrow());
        assertEquals("trace-dubbo", KsetContext.get(KsetContextKeys.TRACE_ID).orElseThrow());
        assertEquals("gray-dubbo", Monitor.currentGrayTag().orElseThrow());
        assertEquals("gray-dubbo", KsetContext.get(KsetContextKeys.GRAY_TAG).orElseThrow());
    }

    static class MapDubboAttachmentAccessor implements DubboAttachmentAccessor {

        private final Map<String, String> attachments;

        MapDubboAttachmentAccessor(Map<String, String> attachments) {
            this.attachments = attachments;
        }

        @Override
        public String getAttachment(String key) {
            return attachments.get(key);
        }

        @Override
        public void setAttachment(String key, String value) {
            attachments.put(key, value);
        }
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
