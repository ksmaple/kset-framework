package com.kset.agent.core.action;

import java.util.LinkedHashMap;
import java.util.Map;

public record ToolCallAction(
        String callId,
        String taskId,
        String toolName,
        Map<String, Object> arguments) implements AgentAction {

    public ToolCallAction {
        if (toolName == null || toolName.isBlank()) {
            throw new IllegalArgumentException("toolName must not be blank");
        }
        callId = callId == null || callId.isBlank() ? toolName : callId;
        arguments = arguments == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(arguments));
    }

    @Override
    public String type() {
        return StandardActionTypes.TOOL_CALL;
    }
}
