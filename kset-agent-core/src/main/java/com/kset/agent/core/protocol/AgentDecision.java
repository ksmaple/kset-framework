package com.kset.agent.core.protocol;

import com.kset.agent.core.action.AgentAction;
import com.kset.agent.core.value.AgentValueSnapshot;

import java.util.List;
import java.util.Map;

/** Protocol-neutral decision consumed by the loop kernel. */
public record AgentDecision(List<AgentAction> actions, Map<String, Object> metadata) {

    public AgentDecision {
        if (actions == null || actions.isEmpty()) {
            throw new IllegalArgumentException("decision must contain at least one action");
        }
        actions = List.copyOf(actions);
        metadata = AgentValueSnapshot.map(metadata);
    }

    public static AgentDecision of(AgentAction action) {
        return new AgentDecision(List.of(action), Map.of());
    }
}
