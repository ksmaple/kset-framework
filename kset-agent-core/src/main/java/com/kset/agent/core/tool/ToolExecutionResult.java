package com.kset.agent.core.tool;

import java.util.LinkedHashMap;
import java.util.Map;

/** Structured result returned by an Agent tool. */
public record ToolExecutionResult(
        boolean success,
        boolean progress,
        Object output,
        String errorCode,
        String errorMessage,
        Map<String, Object> attributes) {

    public ToolExecutionResult {
        attributes = attributes == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(attributes));
    }

    public static ToolExecutionResult success(Object output) {
        return new ToolExecutionResult(true, true, output, null, null, Map.of());
    }

    public static ToolExecutionResult unchanged(Object output) {
        return new ToolExecutionResult(true, false, output, null, null, Map.of());
    }

    public static ToolExecutionResult failure(String code, String message) {
        return new ToolExecutionResult(false, false, null, code, message, Map.of());
    }
}
