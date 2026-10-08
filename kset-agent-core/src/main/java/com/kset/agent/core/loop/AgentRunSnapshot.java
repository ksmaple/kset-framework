package com.kset.agent.core.loop;

import com.kset.agent.core.action.AgentObservation;
import com.kset.agent.core.api.AgentRunStatus;
import com.kset.agent.core.protocol.AgentProtocolId;
import com.kset.agent.core.stop.AgentStopDecision;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Serializable, versioned representation of a loop run. */
public record AgentRunSnapshot(
        int version,
        String runId,
        String task,
        AgentProtocolId protocol,
        AgentRunStatus status,
        int turn,
        Instant startedAt,
        Instant updatedAt,
        List<AgentObservation> observations,
        Map<String, Object> attributes,
        int consecutiveProtocolErrors,
        int consecutiveNoProgress,
        String answer,
        AgentStopDecision stop) {

    public static final int CURRENT_VERSION = 1;

    public AgentRunSnapshot {
        observations = observations == null ? List.of() : List.copyOf(observations);
        attributes = attributes == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(attributes));
    }
}
