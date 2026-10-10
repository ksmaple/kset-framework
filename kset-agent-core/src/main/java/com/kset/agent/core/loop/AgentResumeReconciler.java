package com.kset.agent.core.loop;

import com.kset.agent.core.AgentCoreException;
import com.kset.agent.core.AgentErrorCode;
import com.kset.agent.core.action.AgentAction;
import com.kset.agent.core.action.AgentActionResult;
import com.kset.agent.core.action.AgentObservation;
import com.kset.agent.core.action.ConfirmationAction;
import com.kset.agent.core.action.StandardActionHandlers;
import com.kset.agent.core.action.StandardActionTypes;
import com.kset.agent.core.action.ToolBatchAction;
import com.kset.agent.core.action.ToolCallAction;
import com.kset.agent.core.api.AgentRequest;
import com.kset.agent.core.api.AgentResumeInput;
import com.kset.agent.core.api.AgentRunStatus;
import com.kset.agent.core.protocol.AgentDecision;
import com.kset.agent.core.protocol.AgentProtocolException;
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
            AgentRequest request, AgentRunSnapshot snapshot, AgentRunState state,
            AgentResumeInput input, Instant now) {
        if (!input.hasReconciledObservations()) {
            if (snapshot.stopDecision() != null
                    && snapshot.stopDecision().stopReason()
                    == AgentStopReason.RECONCILIATION_REQUIRED) {
                throw invalid("RECONCILIATION_REQUIRED snapshot requires reconciled observations");
            }
            requirePendingConfirmation(request, snapshot);
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
                input.reconciledObservations(), null, null, null, Map.of(),
                Set.of(StandardActionHandlers.PENDING_ACTION));
        return state.apply(reconciled, now);
    }

    static void validatePendingDecision(AgentRunState state, AgentDecision decision) {
        Object value = state.attributes().get(StandardActionHandlers.PENDING_ACTION);
        if (value == null) {
            return;
        }
        if (!(value instanceof Map<?, ?> pending)) {
            throw new AgentCoreException(AgentErrorCode.INVALID_SNAPSHOT,
                    "pending action state must be a map");
        }
        String type = text(pending.get("type"));
        if (type == null) {
            throw new AgentCoreException(AgentErrorCode.INVALID_SNAPSHOT,
                    "pending action type must not be blank");
        }
        AgentAction firstAction = decision.actions().getFirst();
        boolean matches = switch (type) {
            case StandardActionTypes.CONFIRMATION ->
                    firstAction instanceof ConfirmationAction confirmation
                            && confirmationState(confirmation).equals(pending);
            case StandardActionTypes.TOOL_CALL -> matchesPendingToolCall(firstAction, pending);
            default -> true;
        };
        if (!matches) {
            throw new AgentProtocolException(
                    AgentErrorCode.PENDING_ACTION_MISMATCH.name(),
                    "decision must continue the pending action before other actions");
        }
    }

    private static void requirePendingConfirmation(
            AgentRequest request, AgentRunSnapshot snapshot) {
        Object value = snapshot.attributes().get(StandardActionHandlers.PENDING_ACTION);
        if (value == null) {
            return;
        }
        if (!(value instanceof Map<?, ?> pending)) {
            throw new AgentCoreException(AgentErrorCode.INVALID_SNAPSHOT,
                    "pending action state must be a map");
        }
        String type = text(pending.get("type"));
        if (type == null) {
            throw new AgentCoreException(AgentErrorCode.INVALID_SNAPSHOT,
                    "pending action type must not be blank");
        }
        if (StandardActionTypes.CONFIRMATION.equals(type)) {
            String confirmationId = requiredText(pending.get("confirmationId"),
                    "pending confirmationId");
            if (!request.isConfirmationApproved(confirmationId)) {
                throw invalid("pending confirmation requires approval: " + confirmationId);
            }
        } else if (StandardActionTypes.TOOL_CALL.equals(type)) {
            String callId = requiredText(pending.get("callId"), "pending callId");
            if (!request.isToolCallApproved(callId)) {
                throw invalid("pending tool call requires approval: " + callId);
            }
        }
    }

    private static Map<String, Object> confirmationState(ConfirmationAction confirmation) {
        return Map.of(
                "type", confirmation.actionType(),
                "confirmationId", confirmation.confirmationId(),
                "message", confirmation.message(),
                "options", confirmation.options());
    }

    private static Map<String, Object> toolCallState(ToolCallAction call) {
        Map<String, Object> state = new LinkedHashMap<>(call.operationDefinition());
        state.put("type", call.actionType());
        return state;
    }

    private static boolean matchesPendingToolCall(
            AgentAction action, Map<?, ?> pending) {
        if (action instanceof ToolCallAction call) {
            return toolCallState(call).equals(pending);
        }
        if (action instanceof ToolBatchAction batch) {
            return batch.toolCalls().stream()
                    .anyMatch(call -> toolCallState(call).equals(pending));
        }
        return false;
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
