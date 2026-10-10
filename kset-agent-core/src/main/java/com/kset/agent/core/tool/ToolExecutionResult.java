package com.kset.agent.core.tool;

import com.kset.agent.core.AgentErrorCode;
import com.kset.agent.core.value.AgentValueSnapshot;

import java.util.Map;

/**
 * Structured result returned by an Agent tool. Use {@link AgentErrorCode#TOOL_RESULT_UNKNOWN}
 * when a side effect may have occurred but no authoritative result is available.
 */
public record ToolExecutionResult(
        boolean success,
        boolean progress,
        Object output,
        String errorCode,
        String errorMessage,
        Map<String, Object> attributes) {

    public ToolExecutionResult {
        errorCode = normalize(errorCode);
        errorMessage = normalize(errorMessage);
        if (success && errorCode != null) {
            throw new IllegalArgumentException("successful tool result must not contain an error code");
        }
        if (!success && errorCode == null) {
            throw new IllegalArgumentException("failed tool result requires an error code");
        }
        if (!success && progress) {
            throw new IllegalArgumentException("failed tool result cannot report progress");
        }
        output = AgentValueSnapshot.value(output);
        attributes = AgentValueSnapshot.map(attributes);
    }

    public static ToolExecutionResult success(Object output) {
        return new ToolExecutionResult(true, true, output, null, null, Map.of());
    }

    public static ToolExecutionResult unchanged(Object output) {
        return new ToolExecutionResult(true, false, output, null, null, Map.of());
    }

    public static ToolExecutionResult failure(String errorCode, String errorMessage) {
        return new ToolExecutionResult(
                false, false, null, errorCode, errorMessage, Map.of());
    }

    public static ToolExecutionResult failure(
            AgentErrorCode errorCode, String errorMessage) {
        if (errorCode == null) {
            throw new IllegalArgumentException("tool error code must not be null");
        }
        return failure(errorCode.name(), errorMessage);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
