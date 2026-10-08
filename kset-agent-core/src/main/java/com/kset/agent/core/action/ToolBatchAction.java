package com.kset.agent.core.action;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record ToolBatchAction(String summary, List<ToolCallAction> calls) implements AgentAction {

    public ToolBatchAction {
        if (summary == null || summary.isBlank() || calls == null || calls.size() < 2) {
            throw new IllegalArgumentException("tool batch requires a summary and at least two calls");
        }
        calls = List.copyOf(calls);
        Set<String> callIds = new HashSet<>();
        if (calls.stream().anyMatch(call -> !callIds.add(call.callId()))) {
            throw new IllegalArgumentException("tool batch call ids must be unique");
        }
    }

    @Override
    public String type() {
        return StandardActionTypes.TOOL_BATCH;
    }
}
