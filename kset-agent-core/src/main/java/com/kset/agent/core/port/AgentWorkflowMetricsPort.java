package com.kset.agent.core.port;

public interface AgentWorkflowMetricsPort {
    void record(String taskId, String mode, int totalSteps, int toolCallCount,
                boolean finished, long durationMs, String errorMessage, Long userId);
}
