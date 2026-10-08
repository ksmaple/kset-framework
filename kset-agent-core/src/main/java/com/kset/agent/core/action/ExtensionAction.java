package com.kset.agent.core.action;

import java.util.LinkedHashMap;
import java.util.Map;

/** Action carrier for protocol extensions that introduce a custom action type. */
public record ExtensionAction(String type, Map<String, Object> payload) implements AgentAction {

    public ExtensionAction {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("extension action type must not be blank");
        }
        payload = payload == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(payload));
    }
}
