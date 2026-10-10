package com.kset.agent.core.loop;

import com.kset.agent.core.AgentCoreException;
import com.kset.agent.core.AgentErrorCode;
import com.kset.agent.core.action.AgentObservation;
import com.kset.agent.core.api.AgentRunStatus;
import com.kset.agent.core.protocol.AgentProtocolId;
import com.kset.agent.core.stop.AgentStopDecision;
import com.kset.agent.core.value.AgentValueSnapshot;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Serializable run state. Version 2 separates final answer from suspended wait text. */
public record AgentRunSnapshot(
        int version,
        String runId,
        String task,
        AgentProtocolId protocolId,
        AgentRunStatus runStatus,
        int turn,
        Instant startedAt,
        Instant updatedAt,
        List<AgentObservation> observations,
        Map<String, Object> attributes,
        int consecutiveProtocolErrors,
        int consecutiveNoProgress,
        String answer,
        String suspensionMessage,
        AgentStopDecision stopDecision) {

    public static final int CURRENT_VERSION = 2;

    public AgentRunSnapshot {
        if (version != CURRENT_VERSION) {
            throw new AgentCoreException(AgentErrorCode.UNSUPPORTED_SNAPSHOT_VERSION,
                    "unsupported agent snapshot version: " + version);
        }
        if (runId == null || runId.isBlank() || task == null || task.isBlank()) {
            throw invalid("snapshot runId and task must not be blank");
        }
        if (protocolId == null || runStatus == null
                || startedAt == null || updatedAt == null) {
            throw invalid("snapshot protocolId, runStatus and timestamps must not be null");
        }
        if (turn < 0 || consecutiveProtocolErrors < 0 || consecutiveNoProgress < 0) {
            throw invalid("snapshot counters must not be negative");
        }
        if (updatedAt.isBefore(startedAt)) {
            throw invalid("snapshot updatedAt must not be before startedAt");
        }
        if ((runStatus == AgentRunStatus.RUNNING) != (stopDecision == null)) {
            throw invalid("snapshot status and stop decision are inconsistent");
        }
        if (stopDecision != null
                && (!stopDecision.shouldStop() || stopDecision.runStatus() != runStatus)) {
            throw invalid("snapshot stop decision does not match status");
        }
        if (runStatus == AgentRunStatus.COMPLETED
                && (answer == null || answer.isBlank() || suspensionMessage != null)) {
            throw invalid("completed snapshot requires only a non-blank answer");
        }
        if (runStatus != AgentRunStatus.COMPLETED && answer != null) {
            throw invalid("snapshot answer is only valid for a completed run");
        }
        if (suspensionMessage != null
                && (runStatus != AgentRunStatus.SUSPENDED || suspensionMessage.isBlank())) {
            throw invalid("snapshot suspensionMessage must be non-blank and suspended");
        }
        try {
            observations = observations == null ? List.of() : List.copyOf(observations);
            attributes = AgentValueSnapshot.map(attributes);
        } catch (RuntimeException error) {
            throw new AgentCoreException(AgentErrorCode.INVALID_SNAPSHOT,
                    "snapshot collections must not contain null keys or values", error);
        }
    }

    private static AgentCoreException invalid(String errorMessage) {
        return new AgentCoreException(AgentErrorCode.INVALID_SNAPSHOT, errorMessage);
    }
}
