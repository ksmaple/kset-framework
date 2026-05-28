package com.kset.monitor.reporter;

import com.kset.monitor.facade.MetricKind;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 简单客户端指标计数聚合。
 */
public final class DefaultMetricAggregator implements MetricAggregator {

    private final ConcurrentHashMap<String, AtomicLong> counts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> durations = new ConcurrentHashMap<>();

    @Override
    public void record(String name, long value, MetricKind kind) {
        if (name == null || name.isBlank()) {
            return;
        }
        if (kind == MetricKind.COUNT) {
            counts.computeIfAbsent(name, k -> new AtomicLong()).addAndGet(value);
        } else {
            durations.computeIfAbsent(name, k -> new AtomicLong()).addAndGet(value);
        }
    }

    @Override
    public String flushSummary() {
        if (counts.isEmpty() && durations.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, AtomicLong> e : counts.entrySet()) {
            sb.append("count[").append(e.getKey()).append("]=").append(e.getValue().getAndSet(0)).append(' ');
        }
        for (Map.Entry<String, AtomicLong> e : durations.entrySet()) {
            sb.append("duration[").append(e.getKey()).append("]=").append(e.getValue().getAndSet(0)).append(' ');
        }
        return sb.toString().trim();
    }
}
