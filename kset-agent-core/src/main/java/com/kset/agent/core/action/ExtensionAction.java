package com.kset.agent.core.action;

import com.kset.agent.core.value.AgentValueSnapshot;

import java.util.Map;

/** Action carrier for protocol extensions that introduce a custom action type. */
public record ExtensionAction(
        String actionType,
        Map<String, Object> payload) implements AgentAction {

    public ExtensionAction {
        if (actionType == null || actionType.isBlank()) {
            throw new IllegalArgumentException("actionType must not be blank");
        }
        actionType = actionType.trim();
        payload = AgentValueSnapshot.map(payload);
    }
}
