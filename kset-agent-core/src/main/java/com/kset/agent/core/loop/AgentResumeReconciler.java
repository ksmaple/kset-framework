package com.kset.agent.core.loop;

import com.kset.agent.core.AgentCoreException;
import com.kset.agent.core.AgentErrorCode;
import com.kset.agent.core.action.AgentActionResult;
import com.kset.agent.core.action.AgentObservation;
import com.kset.agent.core.action.StandardActionHandlers;
import com.kset.agent.core.action.StandardActionTypes;
import com.kset.agent.core.api.AgentResumeInput;
import com.kset.agent.core.api.AgentRunStatus;
import com.kset.agent.core.stop.AgentStopReason;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Validates and applies authoritative tool results before a resumed model turn. */
final class AgentResumeReconciler {

    private AgentResumeReconciler() {
    }

    static AgentRunState apply(
            AgentRunSnapshot snapshot, AgentRunState state,
            AgentResumeInput input, Instant now) {
        if (!input.hasReconciledObservations()) {
            if (snapshot.stopDecision() != null
                    && snapshot.stopDecision().stopReason()
                    == AgentStopReason.RECONCILIATION_REQUIRED) {
                throw invalid("RECONCILIATION_REQUIRED snapshot requires reconciled observations");
            }
            return state;
        }
        requireReconciliationSnapshot(snapshot);
        Map<String, PendingToolCall> pending = pendingCalls(snapshot);
        Map<String, PendingToolCall> unmatched = new LinkedHashMap<>(pending);
        for (AgentObservation observation : input.reconciledObservations()) {
            validateObservation(observation, unmatched);
        }
        if (!unmatched.isEmpty()) {
            throw mismatch("reconciled observations do not cover pending call ids: "
                    + unmatched.keySet());
        }
        AgentActionResult reconciled = new AgentActionResult(
                input.reconciledObservations(), null, null, Map.of(),
                Set.of(StandardActionHandlers.PENDING_ACTION));
        return state.apply(reconciled, now);
    }

    private static void requireReconciliationSnapshot(AgentRunSnapshot snapshot) {
        if (snapshot.runStatus() != AgentRunStatus.SUSPENDED
                || snapshot.stopDecision() == null
                || snapshot.stopDecision().stopReason()
                != AgentStopReason.RECONCILIATION_REQUIRED) {
            throw invalid("reconciled observations require a RECONCILIATION_REQUIRED snapshot");
        }
    }

    private static Map<String, PendingToolCall> pendingCalls(AgentRunSnapshot snapshot) {
        Object value = snapshot.attributes().get(StandardActionHandlers.PENDING_ACTION);
        if (!(value instanceof Map<?, ?> pending)) {
            throw mismatch("reconciliation snapshot does not contain a pending action");
        }
        String type = text(pending.get("type"));
        Map<String, PendingToolCall> pendingToolCalls = new LinkedHashMap<>();
        if (StandardActionTypes.TOOL_CALL.equals(type)) {
            addPendingCall(pendingToolCalls, pending);
        } else if (StandardActionTypes.TOOL_BATCH.equals(type)) {
            Object toolCalls = pending.get("toolCalls");
            if (!(toolCalls instanceof Collection<?> values) || values.isEmpty()) {
                throw mismatch("pending tool batch does not contain tool calls");
            }
            for (Object item : values) {
                if (!(item instanceof Map<?, ?> call)) {
                    throw mismatch("pending tool batch contains an invalid tool call");
                }
                addPendingCall(pendingToolCalls, call);
            }
        } else {
            throw mismatch("pending action is not a tool call or tool batch");
        }
        return Map.copyOf(pendingToolCalls);
    }

    private static void addPendingCall(
            Map<String, PendingToolCall> pendingToolCalls, Map<?, ?> pendingValue) {
        String callId = requiredText(pendingValue.get("callId"), "pending callId");
        String toolName = requiredText(pendingValue.get("toolName"), "pending toolName");
        PendingToolCall pendingToolCall = new PendingToolCall(
                callId, text(pendingValue.get("taskId")), toolName);
        if (pendingToolCalls.putIfAbsent(callId, pendingToolCall) != null) {
            throw mismatch("pending action contains duplicate callId: " + callId);
        }
    }

    private static void validateObservation(
            AgentObservation observation, Map<String, PendingToolCall> unmatched) {
        if (!StandardActionTypes.TOOL_CALL.equals(observation.actionType())) {
            throw invalid("reconciled observation actionType must be tool_call");
        }
        if (AgentErrorCode.TOOL_RESULT_UNKNOWN.name().equals(observation.errorCode())) {
            throw invalid("reconciled observation must contain an authoritative result");
        }
        String callId = requiredText(
                observation.attributes().get(AgentObservation.TOOL_CALL_ID), "observation callId");
        PendingToolCall expected = unmatched.remove(callId);
        if (expected == null) {
            throw mismatch("observation callId is not pending or is duplicated: " + callId);
        }
        String toolName = requiredText(
                observation.attributes().get(AgentObservation.TOOL_NAME), "observation toolName");
        String taskId = text(observation.attributes().get(AgentObservation.TOOL_TASK_ID));
        if (!expected.toolName().equals(toolName) || !Objects.equals(expected.taskId(), taskId)) {
            throw mismatch("observation identity does not match pending callId: " + callId);
        }
    }

    private static String requiredText(Object fieldValue, String fieldName) {
        String text = text(fieldValue);
        if (text == null) {
            throw mismatch(fieldName + " must not be blank");
        }
        return text;
    }

    private static String text(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return String.valueOf(value).trim();
    }

    private static AgentCoreException invalid(String errorMessage) {
        return new AgentCoreException(AgentErrorCode.INVALID_RESUME_INPUT, errorMessage);
    }

    private static AgentCoreException mismatch(String errorMessage) {
        return new AgentCoreException(AgentErrorCode.PENDING_ACTION_MISMATCH, errorMessage);
    }

    private record PendingToolCall(String callId, String taskId, String toolName) {
    }
}
