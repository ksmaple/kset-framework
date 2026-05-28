package com.kset.monitor.backend;

import com.kset.monitor.facade.CompletedTransaction;
import com.kset.monitor.facade.MonitorEvent;
import com.kset.monitor.facade.MonitorMetric;

/**
 * 监控后端策略（CAT / SkyWalking / Prometheus / Log 等实现）。
 */
public interface MonitorBackend {

    boolean isEnabled();

    void completeTransaction(CompletedTransaction transaction);

    void logEvent(MonitorEvent event);

    void logMetric(MonitorMetric metric);

    void logError(Throwable throwable, String message);
}
