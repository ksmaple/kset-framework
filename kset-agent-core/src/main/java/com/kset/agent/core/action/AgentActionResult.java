package com.kset.agent.core.action;

import com.kset.agent.core.api.AgentRunStatus;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Outcome of one dispatched action. */
public record AgentActionResult(
        List<AgentObservation> observations,
        AgentRunStatus terminalStatus,
        String answer,
        Map<String, Object> stateAttributes) {

    public AgentActionResult {
        observations = observations == null ? List.of() : List.copyOf(observations);
        stateAttributes = stateAttributes == null
                ? Map.of() : Map.copyOf(new LinkedHashMap<>(stateAttributes));
    }

    public static AgentActionResult observed(AgentObservation observation) {
        return new AgentActionResult(List.of(observation), null, null, Map.of());
    }

    public static AgentActionResult completed(String answer) {
        return new AgentActionResult(List.of(), AgentRunStatus.COMPLETED, answer, Map.of());
    }

    public static AgentActionResult suspended(String message, Map<String, Object> attributes) {
        return new AgentActionResult(List.of(), AgentRunStatus.SUSPENDED, message, attributes);
    }

    public boolean madeProgress() {
        return terminalStatus != null || observations.stream().anyMatch(AgentObservation::progress);
    }
}
