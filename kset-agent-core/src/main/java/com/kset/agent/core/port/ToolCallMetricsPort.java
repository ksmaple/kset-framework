package com.kset.agent.core.port;

public interface ToolCallMetricsPort {
    void record(String toolName, String taskId, long durationMs, boolean success,
                String errorMessage, Long userId);
}
