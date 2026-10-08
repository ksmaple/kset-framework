package com.kset.agent.core.api;

import com.kset.agent.core.loop.AgentLoopOptions;
import com.kset.agent.core.protocol.AgentProtocolId;
import com.kset.agent.core.stop.AgentCancellation;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Input to a new or resumed agent run. */
public record AgentRequest(
        String runId,
        String task,
        AgentProtocolId protocol,
        AgentLoopOptions options,
        Map<String, Object> attributes,
        AgentCancellation cancellation) {

    public static final String CONFIRMED_ACTION_IDS = "agent.confirmedActionIds";

    public AgentRequest {
        runId = runId == null || runId.isBlank() ? UUID.randomUUID().toString() : runId.trim();
        if (task == null || task.isBlank()) {
            throw new IllegalArgumentException("task must not be blank");
        }
        task = task.trim();
        protocol = Objects.requireNonNull(protocol, "protocol");
        options = options == null ? AgentLoopOptions.defaults() : options;
        attributes = attributes == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(attributes));
        cancellation = cancellation == null ? AgentCancellation.none() : cancellation;
    }

    public static AgentRequest of(String task, AgentProtocolId protocol) {
        return new AgentRequest(null, task, protocol, AgentLoopOptions.defaults(), Map.of(),
                AgentCancellation.none());
    }

    public boolean isActionConfirmed(String actionId) {
        if (actionId == null || actionId.isBlank()) {
            return false;
        }
        Object value = attributes.get(CONFIRMED_ACTION_IDS);
        if (value instanceof Collection<?> values) {
            return values.stream().map(String::valueOf).anyMatch(actionId::equals);
        }
        return actionId.equals(String.valueOf(value));
    }
}
