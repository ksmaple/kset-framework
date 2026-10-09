package com.kset.agent.core.loop;

import com.kset.agent.core.AgentCoreException;
import com.kset.agent.core.AgentErrorCode;
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
        if (version < 1) {
            throw invalid("snapshot version must be positive");
        }
        if (runId == null || runId.isBlank() || task == null || task.isBlank()) {
            throw invalid("snapshot runId and task must not be blank");
        }
        if (protocol == null || status == null || startedAt == null || updatedAt == null) {
            throw invalid("snapshot protocol, status and timestamps must not be null");
        }
        if (turn < 0 || consecutiveProtocolErrors < 0 || consecutiveNoProgress < 0) {
            throw invalid("snapshot counters must not be negative");
        }
        if (updatedAt.isBefore(startedAt)) {
            throw invalid("snapshot updatedAt must not be before startedAt");
        }
        if ((status == AgentRunStatus.RUNNING) != (stop == null)) {
            throw invalid("snapshot status and stop decision are inconsistent");
        }
        if (stop != null && (!stop.stop() || stop.status() != status)) {
            throw invalid("snapshot stop decision does not match status");
        }
        try {
            observations = observations == null ? List.of() : List.copyOf(observations);
            attributes = attributes == null
                    ? Map.of() : Map.copyOf(new LinkedHashMap<>(attributes));
        } catch (RuntimeException error) {
            throw new AgentCoreException(AgentErrorCode.INVALID_SNAPSHOT,
                    "snapshot collections must not contain null keys or values", error);
        }
    }

    private static AgentCoreException invalid(String message) {
        return new AgentCoreException(AgentErrorCode.INVALID_SNAPSHOT, message);
    }
}
