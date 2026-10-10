package com.kset.agent.core.action;

import com.kset.agent.core.value.AgentValueSnapshot;

import java.util.LinkedHashMap;
import java.util.Map;

public record ToolCallAction(
        String callId,
        String taskId,
        String toolName,
        Map<String, Object> arguments) implements AgentAction {

    public ToolCallAction {
        if (callId == null || callId.isBlank()) {
            throw new IllegalArgumentException("callId must not be blank");
        }
        if (toolName == null || toolName.isBlank()) {
            throw new IllegalArgumentException("toolName must not be blank");
        }
        callId = callId.trim();
        taskId = taskId == null || taskId.isBlank() ? null : taskId.trim();
        toolName = toolName.trim();
        arguments = AgentValueSnapshot.map(arguments);
    }

    @Override
    public String actionType() {
        return StandardActionTypes.TOOL_CALL;
    }

    /** Operation details checked against the run ledger; not the tool idempotency key. */
    public Map<String, Object> operationDefinition() {
        Map<String, Object> identity = new LinkedHashMap<>();
        identity.put("callId", callId);
        if (taskId != null) {
            identity.put("taskId", taskId);
        }
        identity.put("toolName", toolName);
        identity.put("arguments", arguments);
        return Map.copyOf(identity);
    }
}
