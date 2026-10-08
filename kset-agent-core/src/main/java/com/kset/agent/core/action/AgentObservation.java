package com.kset.agent.core.action;

import java.util.LinkedHashMap;
import java.util.Map;

/** Structured result fed back to the next reasoning turn. */
public record AgentObservation(
        String actionType,
        boolean success,
        boolean progress,
        Object output,
        String errorCode,
        String errorMessage,
        Map<String, Object> attributes) {

    public AgentObservation {
        attributes = attributes == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(attributes));
    }

    public static AgentObservation success(String type, Object output, boolean progress) {
        return new AgentObservation(type, true, progress, output, null, null, Map.of());
    }

    public static AgentObservation failure(String type, String code, String message) {
        return new AgentObservation(type, false, false, null, code, message, Map.of());
    }
}
