package com.kset.agent.core.extension.workflow;

import com.kset.agent.core.dto.AgentWorkflowRequest;
import com.kset.agent.core.dto.AgentWorkflowResult;

public interface WorkflowExecutionExtension {
    String code();

    default int order() {
        return 0;
    }

    default boolean critical() {
        return false;
    }

    default void beforeExecute(String taskId, AgentWorkflowRequest request) {
    }

    default void afterExecute(String taskId, Long userId,
                              AgentWorkflowRequest request, AgentWorkflowResult result) {
    }

    default void onError(String taskId, AgentWorkflowRequest request, RuntimeException error) {
    }
}
