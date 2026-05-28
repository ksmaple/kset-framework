package com.kset.monitor.facade;

import java.util.Optional;

/**
 * 监控事件载荷（对齐 CAT Event）。
 */
public record MonitorEvent(
        String type,
        String name,
        MonitorStatus status,
        String data,
        Optional<String> traceId
) {

    public MonitorEvent(String type, String name, MonitorStatus status, String data) {
        this(type, name, status, data, Optional.empty());
    }
}
