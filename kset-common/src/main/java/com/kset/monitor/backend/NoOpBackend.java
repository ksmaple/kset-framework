package com.kset.monitor.backend;

import com.kset.monitor.facade.CompletedTransaction;
import com.kset.monitor.facade.MonitorEvent;
import com.kset.monitor.facade.MonitorMetric;

/**
 * 未装配具体后端时的占位实现。
 */
public final class NoOpBackend implements MonitorBackend {

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public void completeTransaction(CompletedTransaction transaction) {
    }

    @Override
    public void logEvent(MonitorEvent event) {
    }

    @Override
    public void logMetric(MonitorMetric metric) {
    }

    @Override
    public void logError(Throwable throwable, String message) {
    }
}
