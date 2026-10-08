package com.kset.agent.core.loop;

import com.kset.agent.core.action.AgentActionResult;
import com.kset.agent.core.action.AgentObservation;
import com.kset.agent.core.api.AgentRequest;
import com.kset.agent.core.api.AgentRunStatus;
import com.kset.agent.core.protocol.AgentDecision;
import com.kset.agent.core.protocol.AgentProtocolId;
import com.kset.agent.core.stop.AgentStopDecision;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable runtime state owned by the loop kernel. */
public final class AgentRunState {

    private final String runId;
    private final String task;
    private final AgentProtocolId protocol;
    private final AgentRunStatus status;
    private final int turn;
    private final Instant startedAt;
    private final Instant updatedAt;
    private final List<AgentObservation> observations;
    private final Map<String, Object> attributes;
    private final int consecutiveProtocolErrors;
    private final int consecutiveNoProgress;
    private final String answer;
    private final AgentStopDecision stop;

    private AgentRunState(String runId, String task, AgentProtocolId protocol, AgentRunStatus status,
                          int turn, Instant startedAt, Instant updatedAt,
                          List<AgentObservation> observations, Map<String, Object> attributes,
                          int consecutiveProtocolErrors, int consecutiveNoProgress,
                          String answer, AgentStopDecision stop) {
        this.runId = runId;
        this.task = task;
        this.protocol = protocol;
        this.status = status;
        this.turn = turn;
        this.startedAt = startedAt;
        this.updatedAt = updatedAt;
        this.observations = List.copyOf(observations);
        this.attributes = Map.copyOf(new LinkedHashMap<>(attributes));
        this.consecutiveProtocolErrors = consecutiveProtocolErrors;
        this.consecutiveNoProgress = consecutiveNoProgress;
        this.answer = answer;
        this.stop = stop;
    }

    public static AgentRunState start(AgentRequest request, Instant now) {
        return new AgentRunState(request.runId(), request.task(), request.protocol(), AgentRunStatus.RUNNING,
                0, now, now, List.of(), request.attributes(), 0, 0, null, null);
    }

    public static AgentRunState restore(AgentRunSnapshot snapshot, Instant now) {
        if (snapshot.version() != AgentRunSnapshot.CURRENT_VERSION) {
            throw new IllegalArgumentException("unsupported agent snapshot version: " + snapshot.version());
        }
        return new AgentRunState(snapshot.runId(), snapshot.task(), snapshot.protocol(), AgentRunStatus.RUNNING,
                snapshot.turn(), snapshot.startedAt(), now, snapshot.observations(), snapshot.attributes(),
                snapshot.consecutiveProtocolErrors(), snapshot.consecutiveNoProgress(),
                snapshot.answer(), null);
    }

    public AgentRunState nextTurn(Instant now) {
        return copy(AgentRunStatus.RUNNING, turn + 1, now, observations, attributes,
                consecutiveProtocolErrors, consecutiveNoProgress, answer, null);
    }

    public AgentRunState protocolFailed(String code, String message, Instant now) {
        Map<String, Object> nextAttributes = new LinkedHashMap<>(attributes);
        nextAttributes.put("agent.lastProtocolErrorCode", code);
        nextAttributes.put("agent.lastProtocolError", message);
        return copy(AgentRunStatus.RUNNING, turn, now, observations, nextAttributes,
                consecutiveProtocolErrors + 1, consecutiveNoProgress, answer, null);
    }

    public AgentRunState decisionAccepted(AgentDecision decision, Instant now) {
        Map<String, Object> nextAttributes = new LinkedHashMap<>(attributes);
        nextAttributes.remove("agent.lastProtocolErrorCode");
        nextAttributes.remove("agent.lastProtocolError");
        nextAttributes.putAll(decision.metadata());
        nextAttributes.put("agent.lastActionTypes", decision.actions().stream().map(action -> action.type()).toList());
        return copy(status, turn, now, observations, nextAttributes,
                0, consecutiveNoProgress, answer, stop);
    }

    public AgentRunState withAttributes(Map<String, Object> values, Instant now) {
        Map<String, Object> nextAttributes = new LinkedHashMap<>(attributes);
        if (values != null) {
            nextAttributes.putAll(values);
        }
        return copy(status, turn, now, observations, nextAttributes,
                consecutiveProtocolErrors, consecutiveNoProgress, answer, stop);
    }

    public AgentRunState apply(AgentActionResult result, Instant now) {
        List<AgentObservation> nextObservations = new ArrayList<>(observations);
        nextObservations.addAll(result.observations());
        Map<String, Object> nextAttributes = new LinkedHashMap<>(attributes);
        nextAttributes.putAll(result.stateAttributes());
        int noProgress = result.madeProgress() ? 0 : consecutiveNoProgress + 1;
        AgentRunStatus nextStatus = result.terminalStatus() == null ? status : result.terminalStatus();
        String nextAnswer = result.answer() == null ? answer : result.answer();
        return copy(nextStatus, turn, now, nextObservations, nextAttributes,
                consecutiveProtocolErrors, noProgress, nextAnswer, stop);
    }

    public AgentRunState stopped(AgentStopDecision decision, Instant now) {
        return copy(decision.status(), turn, now, observations, attributes,
                consecutiveProtocolErrors, consecutiveNoProgress, answer, decision);
    }

    public AgentRunSnapshot snapshot() {
        return new AgentRunSnapshot(AgentRunSnapshot.CURRENT_VERSION, runId, task, protocol, status,
                turn, startedAt, updatedAt, observations, attributes,
                consecutiveProtocolErrors, consecutiveNoProgress, answer, stop);
    }

    private AgentRunState copy(AgentRunStatus nextStatus, int nextTurn, Instant nextUpdatedAt,
                               List<AgentObservation> nextObservations,
                               Map<String, Object> nextAttributes,
                               int nextProtocolErrors, int nextNoProgress,
                               String nextAnswer, AgentStopDecision nextStop) {
        return new AgentRunState(runId, task, protocol, nextStatus, nextTurn, startedAt, nextUpdatedAt,
                nextObservations, nextAttributes, nextProtocolErrors, nextNoProgress,
                nextAnswer, nextStop);
    }

    public String runId() { return runId; }
    public String task() { return task; }
    public AgentProtocolId protocol() { return protocol; }
    public AgentRunStatus status() { return status; }
    public int turn() { return turn; }
    public Instant startedAt() { return startedAt; }
    public Instant updatedAt() { return updatedAt; }
    public List<AgentObservation> observations() { return observations; }
    public Map<String, Object> attributes() { return attributes; }
    public int consecutiveProtocolErrors() { return consecutiveProtocolErrors; }
    public int consecutiveNoProgress() { return consecutiveNoProgress; }
    public String answer() { return answer; }
    public AgentStopDecision stop() { return stop; }
}
