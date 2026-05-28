package com.kset.monitor.facade;

import java.util.Map;
import java.util.Optional;

/**
 * 已完成事务快照，供 {@link com.kset.monitor.backend.MonitorBackend} 上报。
 */
public record CompletedTransaction(
        String type,
        String name,
        long durationMs,
        MonitorStatus status,
        Map<String, String> data,
        Optional<String> traceId,
        Optional<String> parentType
) {
}
