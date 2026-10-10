package com.kset.agent.core.action;

import com.kset.agent.core.api.AgentRunStatus;
import com.kset.agent.core.value.AgentValueSnapshot;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Outcome of one dispatched action. State removals are applied before state writes. A terminal
 * result may only complete or suspend the run. {@code answer} is only a completed final answer;
 * {@code suspensionMessage} is only a prompt for a suspended run. Technical failures must be
 * thrown so the kernel can expose an {@code AgentFailure}.
 */
public record AgentActionResult(
        List<AgentObservation> observations,
        AgentRunStatus terminalRunStatus,
        String answer,
        String suspensionMessage,
        Map<String, Object> stateAttributes,
        Set<String> removedStateAttributes) {

    public AgentActionResult {
        if (terminalRunStatus != null
                && terminalRunStatus != AgentRunStatus.COMPLETED
                && terminalRunStatus != AgentRunStatus.SUSPENDED) {
            throw new IllegalArgumentException(
                    "terminalRunStatus must be COMPLETED or SUSPENDED");
        }
        if (terminalRunStatus == null && (answer != null || suspensionMessage != null)) {
            throw new IllegalArgumentException("non-terminal action result must not provide terminal content");
        }
        if (terminalRunStatus == AgentRunStatus.COMPLETED
                && ((answer == null || answer.isBlank()) || suspensionMessage != null)) {
            throw new IllegalArgumentException("completed action result requires only a non-blank answer");
        }
        if (terminalRunStatus == AgentRunStatus.SUSPENDED
                && (answer != null || suspensionMessage == null || suspensionMessage.isBlank())) {
            throw new IllegalArgumentException("suspended action result requires only a non-blank suspensionMessage");
        }
        observations = observations == null ? List.of() : List.copyOf(observations);
        stateAttributes = AgentValueSnapshot.map(stateAttributes);
        removedStateAttributes = removedStateAttributes == null
                ? Set.of() : Set.copyOf(removedStateAttributes);
    }

    public static AgentActionResult observed(AgentObservation observation) {
        return observed(observation, Set.of());
    }

    public static AgentActionResult observed(
            AgentObservation observation, Set<String> removedStateAttributes) {
        return new AgentActionResult(
                List.of(observation), null, null, null, Map.of(), removedStateAttributes);
    }

    public static AgentActionResult completed(String answer) {
        return completed(answer, Set.of());
    }

    public static AgentActionResult completed(String answer, Set<String> removedStateAttributes) {
        return new AgentActionResult(
                List.of(), AgentRunStatus.COMPLETED, answer, null, Map.of(), removedStateAttributes);
    }

    public static AgentActionResult suspended(
            String suspensionMessage, Map<String, Object> stateAttributes) {
        return new AgentActionResult(
                List.of(), AgentRunStatus.SUSPENDED,
                null, suspensionMessage, stateAttributes, Set.of());
    }

    public boolean madeProgress() {
        return terminalRunStatus != null
                || observations.stream().anyMatch(AgentObservation::progress);
    }
}
