package com.kset.monitor;

import com.kset.common.monitor.internal.NoOpMonitorFacade;
import com.kset.monitor.facade.MetricKind;
import com.kset.monitor.facade.MonitorStatus;
import com.kset.monitor.facade.MonitorTransaction;
import com.kset.monitor.facade.MonitorTypes;
import com.kset.monitor.internal.DefaultMonitorFacade;
import com.kset.monitor.backend.LogBackend;
import com.kset.monitor.reporter.DefaultMetricAggregator;
import com.kset.monitor.reporter.SyncAsyncReporter;
import com.kset.monitor.sampler.RateSampler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MonitorFacadeTest {

    @AfterEach
    void resetFacade() {
        Monitor.install(new NoOpMonitorFacade());
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
}
