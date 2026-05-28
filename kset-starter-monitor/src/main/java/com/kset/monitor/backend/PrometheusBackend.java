package com.kset.monitor.backend;

import com.kset.monitor.facade.CompletedTransaction;
import com.kset.monitor.facade.MonitorEvent;
import com.kset.monitor.facade.MonitorMetric;

/**
 * Prometheus 后端占位（Phase 2 接入 Micrometer 后实现）。
 */
public final class PrometheusBackend implements MonitorBackend {

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public void completeTransaction(CompletedTransaction transaction) {
        throw new UnsupportedOperationException("Prometheus backend not enabled; set kset.monitor.backend=log");
    }

    @Override
    public void logEvent(MonitorEvent event) {
        throw new UnsupportedOperationException("Prometheus backend not enabled");
    }

    @Override
    public void logMetric(MonitorMetric metric) {
        throw new UnsupportedOperationException("Prometheus backend not enabled");
    }

    @Override
    public void logError(Throwable throwable, String message) {
        throw new UnsupportedOperationException("Prometheus backend not enabled");
    }
}
