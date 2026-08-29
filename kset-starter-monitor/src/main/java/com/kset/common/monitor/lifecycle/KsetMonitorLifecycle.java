package com.kset.common.monitor.lifecycle;

import com.kset.common.lifecycle.AbstractKsetLifecycleComponent;
import com.kset.common.lifecycle.KsetLifecyclePhases;
import com.kset.common.monitor.reporter.AsyncReporter;
import com.kset.common.monitor.reporter.MetricAggregator;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Objects;

/**
 * 监控统一停机组件（{@link KsetLifecyclePhases#PHASE_MONITOR}）：
 * 事件与线程池排空完成后，输出最终聚合指标并关闭异步上报器。
 */
public class KsetMonitorLifecycle extends AbstractKsetLifecycleComponent {

    private final ObjectProvider<MetricAggregator> metricAggregators;
    private final ObjectProvider<AsyncReporter> asyncReporters;

    public KsetMonitorLifecycle(ObjectProvider<MetricAggregator> metricAggregators,
                                ObjectProvider<AsyncReporter> asyncReporters) {
        super("kset-monitor", KsetLifecyclePhases.PHASE_MONITOR);
        this.metricAggregators = Objects.requireNonNull(metricAggregators, "metricAggregators must not be null");
        this.asyncReporters = Objects.requireNonNull(asyncReporters, "asyncReporters must not be null");
    }

    @Override
    protected void doStop() {
        metricAggregators.forEach(aggregator -> {
            try {
                String summary = aggregator.flushSummary();
                if (summary != null && !summary.isBlank()) {
                    log.info("[kset-lifecycle] final metrics flush: {}", summary);
                }
            } catch (RuntimeException e) {
                log.warn("[kset-lifecycle] failed to flush metrics on shutdown", e);
            }
        });
        asyncReporters.forEach(reporter -> {
            try {
                reporter.shutdown();
            } catch (RuntimeException e) {
                log.warn("[kset-lifecycle] failed to shutdown async reporter", e);
            }
        });
    }
}
