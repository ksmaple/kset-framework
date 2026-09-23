package com.kset.agent.port;

public interface AiFlowMetricsPort {
    void record(String eventType, String taskId, String resourceType, boolean success,
                long durationMs, boolean cacheHit, String detail);
}
