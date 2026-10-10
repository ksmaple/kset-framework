package com.kset.agent.core.loop;

import com.kset.agent.core.AgentCoreException;
import com.kset.agent.core.AgentErrorCode;
import com.kset.agent.core.action.AgentObservation;
import com.kset.agent.core.action.StandardActionHandlers;
import com.kset.agent.core.action.StandardActionTypes;
import com.kset.agent.core.api.AgentRunStatus;
import com.kset.agent.core.protocol.AgentProtocolId;
import com.kset.agent.core.stop.AgentStopDecision;
import com.kset.agent.core.stop.AgentStopReason;
import com.kset.agent.core.value.AgentValueSnapshot;

import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Serializable run state. Version 4 preserves approval batches and reconciliation state. */
public record AgentRunSnapshot(
        int version,
        String agentRunId,
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

    public static final int CURRENT_VERSION = 4;

    public AgentRunSnapshot {
        if (version != CURRENT_VERSION) {
            throw new AgentCoreException(AgentErrorCode.UNSUPPORTED_SNAPSHOT_VERSION,
                    "unsupported agent snapshot version: " + version);
        }
        if (agentRunId == null || agentRunId.isBlank() || task == null || task.isBlank()) {
            throw invalid("snapshot agentRunId and task must not be blank");
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
        validatePendingState(runStatus, stopDecision, observations, attributes);
    }

    private static void validatePendingState(
            AgentRunStatus runStatus, AgentStopDecision stopDecision,
            List<AgentObservation> observations, Map<String, Object> attributes) {
        Object pendingValue = attributes.get(StandardActionHandlers.PENDING_ACTION);
        Object batchValue = attributes.get(StandardActionHandlers.PENDING_BATCH);
        Object reconciliationValue = attributes.get(StandardActionHandlers.RECONCILIATION_PENDING);
        if (reconciliationValue != null && !Boolean.TRUE.equals(reconciliationValue)) {
            throw invalid("reconciliation pending marker must be true when present");
        }
        boolean reconciliationPending = Boolean.TRUE.equals(reconciliationValue);
        boolean reconciliationStop = stopDecision != null
                && stopDecision.stopReason() == AgentStopReason.RECONCILIATION_REQUIRED;
        if ((reconciliationStop && !reconciliationPending)
                || (runStatus == AgentRunStatus.SUSPENDED
                && reconciliationPending && !reconciliationStop)) {
            throw invalid("reconciliation marker and stop reason are inconsistent");
        }
        if (reconciliationPending && runStatus != AgentRunStatus.SUSPENDED
                && (runStatus != AgentRunStatus.FAILED || stopDecision.stopReason()
                != AgentStopReason.FATAL_ERROR)) {
            throw invalid("reconciliation pending state must be suspended or fatally failed");
        }
        if (pendingValue == null) {
            if (batchValue != null || reconciliationPending) {
                throw invalid("pending batch or reconciliation requires a pending action");
            }
            return;
        }
        if (!(pendingValue instanceof Map<?, ?> pendingAction)
                || !(pendingAction.get("type") instanceof String pendingType)) {
            throw invalid("pending action must contain a valid type");
        }
        if (!StandardActionTypes.CONFIRMATION.equals(pendingType)
                && !StandardActionTypes.TOOL_CALL.equals(pendingType)
                && !StandardActionTypes.TOOL_BATCH.equals(pendingType)) {
            throw invalid("unsupported pending action type: " + pendingType);
        }
        if (StandardActionTypes.CONFIRMATION.equals(pendingType)) {
            validatePendingConfirmation(pendingAction);
        } else if (StandardActionTypes.TOOL_CALL.equals(pendingType)) {
            validatePendingToolCall(pendingAction, true);
        } else {
            validatePendingToolBatch(pendingAction);
        }
        if (reconciliationPending) {
            if (StandardActionTypes.CONFIRMATION.equals(pendingType) || batchValue != null) {
                throw invalid("reconciliation requires a pending tool action without an approval batch");
            }
        } else if (StandardActionTypes.TOOL_BATCH.equals(pendingType)) {
            throw invalid("pending tool batch requires reconciliation");
        } else if (StandardActionTypes.TOOL_CALL.equals(pendingType)
                && hasUnreconciledToolObservation(observations, pendingAction.get("callId"))) {
            throw invalid("pending tool call with an unknown result requires reconciliation");
        }
        if (batchValue != null) {
            validatePendingBatch(pendingType, pendingAction, batchValue);
        }
    }

    private static void validatePendingConfirmation(Map<?, ?> confirmation) {
        if (!confirmation.keySet().equals(Set.of("type", "confirmationId", "message", "options"))
                || !hasText(confirmation.get("confirmationId"))
                || !hasText(confirmation.get("message"))
                || !(confirmation.get("options") instanceof Map<?, ?>)) {
            throw invalid("pending confirmation is missing required fields");
        }
    }

    private static void validatePendingToolBatch(Map<?, ?> batch) {
        if (!batch.keySet().equals(Set.of("type", "summary", "toolCalls"))
                || !hasText(batch.get("summary"))
                || !(batch.get("toolCalls") instanceof List<?> calls)
                || calls.size() < 2) {
            throw invalid("pending reconciliation batch is missing required fields");
        }
        Set<String> callIds = new HashSet<>();
        for (Object value : calls) {
            if (!(value instanceof Map<?, ?> call)
                    || !callIds.add(validatePendingToolCall(call, true))) {
                throw invalid("pending reconciliation batch contains an invalid or duplicate tool call");
            }
        }
    }

    private static String validatePendingToolCall(Map<?, ?> call, boolean includesType) {
        Set<String> fields = call.containsKey("taskId")
                ? Set.of("callId", "taskId", "toolName", "arguments")
                : Set.of("callId", "toolName", "arguments");
        if (includesType) {
            fields = new HashSet<>(fields);
            fields.add("type");
        }
        if (!call.keySet().equals(fields)
                || (includesType && !StandardActionTypes.TOOL_CALL.equals(call.get("type")))
                || !hasText(call.get("callId"))
                || !hasText(call.get("toolName"))
                || (call.containsKey("taskId") && !hasText(call.get("taskId")))
                || !(call.get("arguments") instanceof Map<?, ?>)) {
            throw invalid("pending tool call is missing required fields");
        }
        return (String) call.get("callId");
    }

    private static boolean hasText(Object value) {
        return value instanceof String text && !text.isBlank();
    }

    private static boolean hasUnreconciledToolObservation(
            List<AgentObservation> observations, Object callId) {
        for (int index = observations.size() - 1; index >= 0; index--) {
            AgentObservation observation = observations.get(index);
            if (StandardActionTypes.TOOL_CALL.equals(observation.actionType())
                    && callId != null && callId.equals(observation.attributes().get(
                    AgentObservation.TOOL_CALL_ID))) {
                return AgentErrorCode.TOOL_RESULT_UNKNOWN.name().equals(observation.errorCode());
            }
        }
        return false;
    }

    private static void validatePendingBatch(
            String pendingType, Map<?, ?> pendingAction, Object batchValue) {
        if (!StandardActionTypes.TOOL_CALL.equals(pendingType)
                || !(batchValue instanceof List<?> calls) || calls.size() < 2) {
            throw invalid("pending approval batch must contain at least two tool calls");
        }
        Map<Object, Object> pendingOperation = new LinkedHashMap<>(pendingAction);
        pendingOperation.remove("type");
        Set<String> callIds = new HashSet<>();
        boolean containsPending = false;
        for (Object value : calls) {
            if (!(value instanceof Map<?, ?> call)
                    || !callIds.add(validatePendingToolCall(call, false))) {
                throw invalid("pending approval batch contains an invalid or duplicate tool call");
            }
            containsPending |= pendingOperation.equals(call);
        }
        if (!containsPending) {
            throw invalid("pending approval batch does not contain the pending tool call");
        }
    }

    private static AgentCoreException invalid(String errorMessage) {
        return new AgentCoreException(AgentErrorCode.INVALID_SNAPSHOT, errorMessage);
    }
}
