package com.kset.agent.core.workflow;

import java.time.Duration;
import java.util.Optional;

/** 工作流跨请求运行状态存储。 */
public interface WorkflowRuntimeStateStore {

    void saveConfirmation(String taskId, String payload, Duration ttl);

    Optional<String> findConfirmation(String taskId);

    void deleteConfirmation(String taskId);

    void requestCancellation(String taskId, Duration ttl);

    boolean isCancellationRequested(String taskId);
}
