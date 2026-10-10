package com.kset.agent.core.loop;

import com.kset.agent.core.AgentCoreException;
import com.kset.agent.core.AgentErrorCode;
import com.kset.agent.core.action.AgentAction;
import com.kset.agent.core.action.AgentActionResult;
import com.kset.agent.core.action.AgentObservation;
import com.kset.agent.core.action.ToolBatchAction;
import com.kset.agent.core.action.ToolCallAction;
import com.kset.agent.core.api.AgentRequest;
import com.kset.agent.core.api.AgentRunStatus;
import com.kset.agent.core.protocol.AgentDecision;
import com.kset.agent.core.protocol.AgentProtocolId;
import com.kset.agent.core.stop.AgentStopDecision;
import com.kset.agent.core.value.AgentValueSnapshot;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable runtime state owned by the loop kernel; answer and wait text are separate. */
public final class AgentRunState {

    public static final String PROTOCOL_METADATA_ATTRIBUTE = "agent.protocolMetadata";
    public static final String TOOL_OPERATIONS_ATTRIBUTE = "agent.toolOperations";

    private final String agentRunId;
    private final String task;
    private final AgentProtocolId protocolId;
    private final AgentRunStatus runStatus;
    private final int turn;
    private final Instant startedAt;
    private final Instant updatedAt;
    private final List<AgentObservation> observations;
    private final Map<String, Object> attributes;
    private final int consecutiveProtocolErrors;
    private final int consecutiveNoProgress;
    private final String answer;
    private final String suspensionMessage;
    private final AgentStopDecision stopDecision;

    private AgentRunState(
            String agentRunId, String task, AgentProtocolId protocolId,
            AgentRunStatus runStatus,
            int turn, Instant startedAt, Instant updatedAt,
            List<AgentObservation> observations, Map<String, Object> attributes,
            int consecutiveProtocolErrors, int consecutiveNoProgress,
            String answer, String suspensionMessage, AgentStopDecision stopDecision) {
        this.agentRunId = agentRunId;
        this.task = task;
        this.protocolId = protocolId;
        this.runStatus = runStatus;
        this.turn = turn;
        this.startedAt = startedAt;
        this.updatedAt = updatedAt;
        this.observations = List.copyOf(observations);
        this.attributes = AgentValueSnapshot.map(attributes);
        this.consecutiveProtocolErrors = consecutiveProtocolErrors;
        this.consecutiveNoProgress = consecutiveNoProgress;
        this.answer = answer;
        this.suspensionMessage = suspensionMessage;
        this.stopDecision = stopDecision;
    }

    static AgentRunState start(AgentRequest request, Instant now) {
        return new AgentRunState(
                request.agentRunId(), request.task(), request.protocolId(), AgentRunStatus.RUNNING,
                0, now, now, List.of(), Map.of(), 0, 0, null, null, null);
    }

    static AgentRunState restore(AgentRunSnapshot snapshot, Instant now) {
        if (snapshot.version() != AgentRunSnapshot.CURRENT_VERSION) {
            throw new AgentCoreException(AgentErrorCode.UNSUPPORTED_SNAPSHOT_VERSION,
                    "unsupported agent snapshot version: " + snapshot.version());
        }
        return new AgentRunState(
                snapshot.agentRunId(), snapshot.task(), snapshot.protocolId(), AgentRunStatus.RUNNING,
                snapshot.turn(), snapshot.startedAt(), now,
                snapshot.observations(), snapshot.attributes(),
                snapshot.consecutiveProtocolErrors(), snapshot.consecutiveNoProgress(),
                null, null, null);
    }

    // agent-core / agent-core-20261010-17: retain the prior answer restoration for rollback.
    @SuppressWarnings("unused")
    private static AgentRunState restoreForRollback(AgentRunSnapshot snapshot, Instant now) {
        if (snapshot.version() != AgentRunSnapshot.CURRENT_VERSION) {
            throw new AgentCoreException(AgentErrorCode.UNSUPPORTED_SNAPSHOT_VERSION,
                    "unsupported agent snapshot version: " + snapshot.version());
        }
        return new AgentRunState(
                snapshot.agentRunId(), snapshot.task(), snapshot.protocolId(), AgentRunStatus.RUNNING,
                snapshot.turn(), snapshot.startedAt(), now,
                snapshot.observations(), snapshot.attributes(),
                snapshot.consecutiveProtocolErrors(), snapshot.consecutiveNoProgress(),
                snapshot.answer(), null, null);
    }

    AgentRunState nextTurn(Instant now) {
        return copy(AgentRunStatus.RUNNING, turn + 1, now, observations, attributes,
                consecutiveProtocolErrors, consecutiveNoProgress, answer, null, null);
    }

    AgentRunState protocolFailed(
            String protocolErrorCode, String errorMessage, Instant now) {
        Map<String, Object> nextAttributes = new LinkedHashMap<>(attributes);
        nextAttributes.put("agent.lastProtocolErrorCode", protocolErrorCode);
        nextAttributes.put("agent.lastProtocolError", errorMessage);
        return copy(AgentRunStatus.RUNNING, turn, now, observations, nextAttributes,
                consecutiveProtocolErrors + 1, consecutiveNoProgress, answer, null, null);
    }

    AgentRunState decisionAccepted(AgentDecision decision, Instant now) {
        Map<String, Object> nextAttributes = new LinkedHashMap<>(attributes);
        nextAttributes.remove("agent.lastProtocolErrorCode");
        nextAttributes.remove("agent.lastProtocolError");
        nextAttributes.put(PROTOCOL_METADATA_ATTRIBUTE, decision.metadata());
        nextAttributes.put("agent.lastActionTypes", decision.actions().stream()
                .map(AgentAction::actionType).toList());
        recordToolOperations(nextAttributes, decision);
        return copy(runStatus, turn, now, observations, nextAttributes,
                0, consecutiveNoProgress, answer, suspensionMessage, stopDecision);
    }

    private static void recordToolOperations(
            Map<String, Object> attributes, AgentDecision decision) {
        Map<String, Object> operations = new LinkedHashMap<>();
        Object existing = attributes.get(TOOL_OPERATIONS_ATTRIBUTE);
        if (existing instanceof Map<?, ?> values) {
            values.forEach((key, value) -> operations.put(String.valueOf(key), value));
        } else if (existing != null) {
            throw new AgentCoreException(AgentErrorCode.INVALID_SNAPSHOT,
                    "tool operation state must be a map");
        }
        for (AgentAction action : decision.actions()) {
            if (action instanceof ToolCallAction call) {
                operations.put(call.callId(), call.operationDefinition());
            } else if (action instanceof ToolBatchAction batch) {
                batch.toolCalls().forEach(call ->
                        operations.put(call.callId(), call.operationDefinition()));
            }
        }
        if (!operations.isEmpty()) {
            attributes.put(TOOL_OPERATIONS_ATTRIBUTE, Map.copyOf(operations));
        }
    }

    public AgentRunState withAttributes(Map<String, Object> values, Instant now) {
        Map<String, Object> nextAttributes = new LinkedHashMap<>(attributes);
        if (values != null) {
            nextAttributes.putAll(values);
        }
        return copy(runStatus, turn, now, observations, nextAttributes,
                consecutiveProtocolErrors, consecutiveNoProgress, answer, suspensionMessage,
                stopDecision);
    }

    AgentRunState apply(AgentActionResult result, Instant now) {
        List<AgentObservation> nextObservations = new ArrayList<>(observations);
        nextObservations.addAll(result.observations());
        Map<String, Object> nextAttributes = new LinkedHashMap<>(attributes);
        result.removedStateAttributes().forEach(nextAttributes::remove);
        nextAttributes.putAll(result.stateAttributes());
        int noProgress = result.madeProgress() ? 0 : consecutiveNoProgress + 1;
        AgentRunStatus nextRunStatus = result.requestedRunStatus() == null
                ? runStatus : result.requestedRunStatus();
        String nextAnswer = nextRunStatus == AgentRunStatus.COMPLETED ? result.answer() : null;
        String nextSuspensionMessage = nextRunStatus == AgentRunStatus.SUSPENDED
                ? result.suspensionMessage() : null;
        return copy(nextRunStatus, turn, now, nextObservations, nextAttributes,
                consecutiveProtocolErrors, noProgress, nextAnswer, nextSuspensionMessage,
                stopDecision);
    }

    // agent-core / agent-core-20261010-17: retain the mixed terminal text mapping for rollback.
    @SuppressWarnings("unused")
    private AgentRunState applyForRollback(AgentActionResult result, Instant now) {
        List<AgentObservation> nextObservations = new ArrayList<>(observations);
        nextObservations.addAll(result.observations());
        Map<String, Object> nextAttributes = new LinkedHashMap<>(attributes);
        result.removedStateAttributes().forEach(nextAttributes::remove);
        nextAttributes.putAll(result.stateAttributes());
        int noProgress = result.madeProgress() ? 0 : consecutiveNoProgress + 1;
        AgentRunStatus nextRunStatus = result.requestedRunStatus() == null
                ? runStatus : result.requestedRunStatus();
        String terminalText = result.requestedRunStatus() == AgentRunStatus.SUSPENDED
                ? result.suspensionMessage() : result.answer();
        String nextAnswer = terminalText == null ? answer : terminalText;
        return copy(nextRunStatus, turn, now, nextObservations, nextAttributes,
                consecutiveProtocolErrors, noProgress, nextAnswer, null, stopDecision);
    }

    AgentRunState stopped(AgentStopDecision decision, Instant now) {
        return copy(decision.runStatus(), turn, now, observations, attributes,
                consecutiveProtocolErrors, consecutiveNoProgress,
                decision.runStatus() == AgentRunStatus.COMPLETED ? answer : null,
                decision.runStatus() == AgentRunStatus.SUSPENDED ? suspensionMessage : null,
                decision);
    }

    // agent-core / agent-core-20261010-17: retain the prior terminal answer mapping for rollback.
    @SuppressWarnings("unused")
    private AgentRunState stoppedForRollback(AgentStopDecision decision, Instant now) {
        return copy(decision.runStatus(), turn, now, observations, attributes,
                consecutiveProtocolErrors, consecutiveNoProgress, answer, null, decision);
    }

    public AgentRunSnapshot snapshot() {
        return new AgentRunSnapshot(
                AgentRunSnapshot.CURRENT_VERSION, agentRunId, task, protocolId, runStatus,
                turn, startedAt, updatedAt, observations, attributes,
                consecutiveProtocolErrors, consecutiveNoProgress, answer, suspensionMessage,
                stopDecision);
    }

    private AgentRunState copy(AgentRunStatus nextRunStatus, int nextTurn, Instant nextUpdatedAt,
                               List<AgentObservation> nextObservations,
                               Map<String, Object> nextAttributes,
                               int nextConsecutiveProtocolErrors,
                               int nextConsecutiveNoProgress,
                               String nextAnswer, String nextSuspensionMessage,
                               AgentStopDecision nextStopDecision) {
        return new AgentRunState(
                agentRunId, task, protocolId, nextRunStatus, nextTurn, startedAt, nextUpdatedAt,
                nextObservations, nextAttributes, nextConsecutiveProtocolErrors,
                nextConsecutiveNoProgress, nextAnswer, nextSuspensionMessage, nextStopDecision);
    }

    public String agentRunId() { return agentRunId; }
    public String task() { return task; }
    public AgentProtocolId protocolId() { return protocolId; }
    public AgentRunStatus runStatus() { return runStatus; }
    public int turn() { return turn; }
    public Instant startedAt() { return startedAt; }
    public Instant updatedAt() { return updatedAt; }
    public List<AgentObservation> observations() { return observations; }
    public Map<String, Object> attributes() { return attributes; }
    public int consecutiveProtocolErrors() { return consecutiveProtocolErrors; }
    public int consecutiveNoProgress() { return consecutiveNoProgress; }
    public String answer() { return answer; }
    public String suspensionMessage() { return suspensionMessage; }
    public AgentStopDecision stopDecision() { return stopDecision; }
}
