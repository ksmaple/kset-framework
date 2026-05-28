package com.kset.monitor.backend;

import com.kset.monitor.facade.CompletedTransaction;
import com.kset.monitor.facade.MonitorEvent;
import com.kset.monitor.facade.MonitorMetric;

/**
 * CAT 后端占位（Phase 2 接入 cat-client 后实现）。
 */
public final class CatBackend implements MonitorBackend {

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public void completeTransaction(CompletedTransaction transaction) {
        throw new UnsupportedOperationException("CAT backend not enabled; set kset.monitor.backend=log");
    }

    @Override
    public void logEvent(MonitorEvent event) {
        throw new UnsupportedOperationException("CAT backend not enabled");
    }

    @Override
    public void logMetric(MonitorMetric metric) {
        throw new UnsupportedOperationException("CAT backend not enabled");
    }

    @Override
    public void logError(Throwable throwable, String message) {
        throw new UnsupportedOperationException("CAT backend not enabled");
    }
}
