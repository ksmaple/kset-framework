package com.kset.agent.core.action;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** A protocol-neutral batch of independent tool calls. */
public record ToolBatchAction(
        String summary,
        List<ToolCallAction> toolCalls) implements AgentAction {

    public ToolBatchAction {
        if (summary == null || summary.isBlank() || toolCalls == null || toolCalls.size() < 2) {
            throw new IllegalArgumentException(
                    "tool batch requires a summary and at least two toolCalls");
        }
        toolCalls = List.copyOf(toolCalls);
        Set<String> callIds = new HashSet<>();
        if (toolCalls.stream().anyMatch(toolCall -> !callIds.add(toolCall.callId()))) {
            throw new IllegalArgumentException("tool batch callIds must be unique");
        }
    }

    @Override
    public String actionType() {
        return StandardActionTypes.TOOL_BATCH;
    }
}
