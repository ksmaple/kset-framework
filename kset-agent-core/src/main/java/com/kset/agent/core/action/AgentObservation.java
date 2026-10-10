package com.kset.agent.core.action;

import com.kset.agent.core.AgentErrorCode;
import com.kset.agent.core.tool.ToolExecutionResult;
import com.kset.agent.core.value.AgentValueSnapshot;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Structured result fed back to the next reasoning turn. */
public record AgentObservation(
        String actionType,
        boolean success,
        boolean progress,
        Object output,
        String errorCode,
        String errorMessage,
        Map<String, Object> attributes) {

    public static final String TOOL_CALL_ID = "callId";
    public static final String TOOL_TASK_ID = "taskId";
    public static final String TOOL_NAME = "toolName";

    public AgentObservation {
        if (actionType == null || actionType.isBlank()) {
            throw new IllegalArgumentException("observation actionType must not be blank");
        }
        actionType = actionType.trim();
        errorCode = normalize(errorCode);
        errorMessage = normalize(errorMessage);
        if (success && errorCode != null) {
            throw new IllegalArgumentException("successful observation must not contain an error code");
        }
        if (!success && errorCode == null) {
            throw new IllegalArgumentException("failed observation requires an error code");
        }
        if (!success && progress) {
            throw new IllegalArgumentException("failed observation cannot report progress");
        }
        output = AgentValueSnapshot.value(output);
        attributes = AgentValueSnapshot.map(attributes);
    }

    public static AgentObservation success(
            String actionType, Object output, boolean progress) {
        return new AgentObservation(
                actionType, true, progress, output, null, null, Map.of());
    }

    public static AgentObservation failure(
            String actionType, String errorCode, String errorMessage) {
        return new AgentObservation(
                actionType, false, false, null, errorCode, errorMessage, Map.of());
    }

    public static AgentObservation failure(
            String actionType, AgentErrorCode errorCode, String errorMessage) {
        return failure(actionType, errorCode.name(), errorMessage);
    }

    /** Creates the fixed tool Observation shape accepted by reconciliation resume. */
    public static AgentObservation toolResult(
            String callId, String taskId, String toolName, ToolExecutionResult result) {
        if (callId == null || callId.isBlank() || toolName == null || toolName.isBlank()) {
            throw new IllegalArgumentException("tool observation callId and toolName must not be blank");
        }
        ToolExecutionResult value = Objects.requireNonNull(result, "result");
        Map<String, Object> attributes = new LinkedHashMap<>(value.attributes());
        attributes.put(TOOL_CALL_ID, callId.trim());
        if (taskId == null || taskId.isBlank()) {
            attributes.remove(TOOL_TASK_ID);
        } else {
            attributes.put(TOOL_TASK_ID, taskId.trim());
        }
        attributes.put(TOOL_NAME, toolName.trim());
        return new AgentObservation(
                StandardActionTypes.TOOL_CALL, value.success(), value.progress(), value.output(),
                value.errorCode(), value.errorMessage(), attributes);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
