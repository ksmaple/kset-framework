package com.kset.monitor.facade;

import java.util.Optional;

/**
 * 监控指标载荷（对齐 CAT Metric）。
 */
public record MonitorMetric(
        String name,
        long value,
        MetricKind kind,
        Optional<String> traceId
) {

    public MonitorMetric(String name, long value, MetricKind kind) {
        this(name, value, kind, Optional.empty());
    }
}
