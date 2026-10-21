package com.kset.agent.core.action;

import com.kset.agent.core.value.AgentValueSnapshot;

import java.util.Collections;
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
        arguments = AgentValueSnapshot.toolFields(arguments);
    }

    @Override
    public String actionType() {
        return StandardActionTypes.TOOL_CALL;
    }

    /** Operation details checked against the run ledger; not the tool idempotency key. */
    public Map<String, Object> operationDefinition() {
        Map<String, Object> identity = new LinkedHashMap<>();
        identity.put("callId", callId);
        identity.put("taskId", taskId);
        identity.put("toolName", toolName);
        identity.put("arguments", arguments);
        return Collections.unmodifiableMap(identity);
    }

    public Map<String, Object> pendingState() {
        Map<String, Object> state = new LinkedHashMap<>(operationDefinition());
        state.put("type", actionType());
        return Collections.unmodifiableMap(state);
    }

    /** Older v4 snapshots may have omitted a null taskId. */
    public boolean matchesStoredOperation(Object stored) {
        return matchesStoredFields(stored, false);
    }

    public boolean matchesStoredPendingState(Object stored) {
        return matchesStoredFields(stored, true);
    }

    private boolean matchesStoredFields(Object stored, boolean pending) {
        if (!(stored instanceof Map<?, ?> fields)) {
            return false;
        }
        Map<String, Object> expected = new LinkedHashMap<>(
                pending ? pendingState() : operationDefinition());
        if (expected.equals(fields)) {
            return true;
        }
        if (taskId != null || fields.containsKey("taskId")) {
            return false;
        }
        expected.remove("taskId");
        return expected.equals(fields);
    }
}
