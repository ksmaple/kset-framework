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

/** Immutable runtime state owned by the loop kernel. */
public final class AgentRunState {

    public static final String PROTOCOL_METADATA_ATTRIBUTE = "agent.protocolMetadata";
    public static final String TOOL_OPERATIONS_ATTRIBUTE = "agent.toolOperations";

    private final String runId;
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
    private final AgentStopDecision stopDecision;

    private AgentRunState(
            String runId, String task, AgentProtocolId protocolId,
            AgentRunStatus runStatus,
            int turn, Instant startedAt, Instant updatedAt,
            List<AgentObservation> observations, Map<String, Object> attributes,
            int consecutiveProtocolErrors, int consecutiveNoProgress,
            String answer, AgentStopDecision stopDecision) {
        this.runId = runId;
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
        this.stopDecision = stopDecision;
    }

    static AgentRunState start(AgentRequest request, Instant now) {
        return new AgentRunState(
                request.runId(), request.task(), request.protocolId(), AgentRunStatus.RUNNING,
                0, now, now, List.of(), Map.of(), 0, 0, null, null);
    }

    static AgentRunState restore(AgentRunSnapshot snapshot, Instant now) {
        if (snapshot.version() != AgentRunSnapshot.CURRENT_VERSION) {
            throw new AgentCoreException(AgentErrorCode.UNSUPPORTED_SNAPSHOT_VERSION,
                    "unsupported agent snapshot version: " + snapshot.version());
        }
        return new AgentRunState(
                snapshot.runId(), snapshot.task(), snapshot.protocolId(), AgentRunStatus.RUNNING,
                snapshot.turn(), snapshot.startedAt(), now,
                snapshot.observations(), snapshot.attributes(),
                snapshot.consecutiveProtocolErrors(), snapshot.consecutiveNoProgress(),
                snapshot.answer(), null);
    }

    AgentRunState nextTurn(Instant now) {
        return copy(AgentRunStatus.RUNNING, turn + 1, now, observations, attributes,
                consecutiveProtocolErrors, consecutiveNoProgress, answer, null);
    }

    AgentRunState protocolFailed(
            String protocolErrorCode, String errorMessage, Instant now) {
        Map<String, Object> nextAttributes = new LinkedHashMap<>(attributes);
        nextAttributes.put("agent.lastProtocolErrorCode", protocolErrorCode);
        nextAttributes.put("agent.lastProtocolError", errorMessage);
        return copy(AgentRunStatus.RUNNING, turn, now, observations, nextAttributes,
                consecutiveProtocolErrors + 1, consecutiveNoProgress, answer, null);
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
                0, consecutiveNoProgress, answer, stopDecision);
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
                operations.put(call.callId(), call.operationIdentity());
            } else if (action instanceof ToolBatchAction batch) {
                batch.toolCalls().forEach(call ->
                        operations.put(call.callId(), call.operationIdentity()));
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
                consecutiveProtocolErrors, consecutiveNoProgress, answer, stopDecision);
    }

    AgentRunState apply(AgentActionResult result, Instant now) {
        List<AgentObservation> nextObservations = new ArrayList<>(observations);
        nextObservations.addAll(result.observations());
        Map<String, Object> nextAttributes = new LinkedHashMap<>(attributes);
        result.removedStateAttributes().forEach(nextAttributes::remove);
        nextAttributes.putAll(result.stateAttributes());
        int noProgress = result.madeProgress() ? 0 : consecutiveNoProgress + 1;
        AgentRunStatus nextRunStatus = result.terminalRunStatus() == null
                ? runStatus : result.terminalRunStatus();
        String nextAnswer = result.answer() == null ? answer : result.answer();
        return copy(nextRunStatus, turn, now, nextObservations, nextAttributes,
                consecutiveProtocolErrors, noProgress, nextAnswer, stopDecision);
    }

    AgentRunState stopped(AgentStopDecision decision, Instant now) {
        return copy(decision.runStatus(), turn, now, observations, attributes,
                consecutiveProtocolErrors, consecutiveNoProgress, answer, decision);
    }

    public AgentRunSnapshot snapshot() {
        return new AgentRunSnapshot(
                AgentRunSnapshot.CURRENT_VERSION, runId, task, protocolId, runStatus,
                turn, startedAt, updatedAt, observations, attributes,
                consecutiveProtocolErrors, consecutiveNoProgress, answer, stopDecision);
    }

    private AgentRunState copy(AgentRunStatus nextRunStatus, int nextTurn, Instant nextUpdatedAt,
                               List<AgentObservation> nextObservations,
                               Map<String, Object> nextAttributes,
                               int nextConsecutiveProtocolErrors,
                               int nextConsecutiveNoProgress,
                               String nextAnswer, AgentStopDecision nextStopDecision) {
        return new AgentRunState(
                runId, task, protocolId, nextRunStatus, nextTurn, startedAt, nextUpdatedAt,
                nextObservations, nextAttributes, nextConsecutiveProtocolErrors,
                nextConsecutiveNoProgress, nextAnswer, nextStopDecision);
    }

    public String runId() { return runId; }
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
    public AgentStopDecision stopDecision() { return stopDecision; }
}
