package com.kset.agent.core.api;

import com.kset.agent.core.loop.AgentRunSnapshot;
import com.kset.agent.core.stop.AgentStopDecision;

import java.util.Objects;

/**
 * Immutable result of one synchronous run or resume invocation.
 * The run status and stop decision always describe the same terminal or suspended state.
 * {@code answer} is non-null only for a completed run; a wait prompt is in
 * {@code stopDecision.stopMessage}, never in {@code answer}.
 */
public record AgentResult(
        String agentRunId,
        AgentRunStatus runStatus,
        String answer,
        AgentStopDecision stopDecision,
        AgentRunSnapshot snapshot,
        AgentFailure failure) {

    public AgentResult {
        if (agentRunId == null || agentRunId.isBlank()) {
            throw new IllegalArgumentException("agentRunId must not be blank");
        }
        Objects.requireNonNull(runStatus, "runStatus");
        Objects.requireNonNull(stopDecision, "stopDecision");
        Objects.requireNonNull(snapshot, "snapshot");
        if (runStatus == AgentRunStatus.RUNNING || !stopDecision.shouldStop()
                || stopDecision.runStatus() != runStatus
                || snapshot.runStatus() != runStatus
                || !stopDecision.equals(snapshot.stopDecision())
                || !agentRunId.equals(snapshot.agentRunId())) {
            throw new IllegalArgumentException("result status, stop decision and snapshot must match");
        }
        if (runStatus == AgentRunStatus.COMPLETED && (answer == null || answer.isBlank())) {
            throw new IllegalArgumentException("completed result requires a non-blank answer");
        }
        if (runStatus != AgentRunStatus.COMPLETED && answer != null) {
            throw new IllegalArgumentException("answer is only valid for a completed run");
        }
        if (!Objects.equals(answer, snapshot.answer())) {
            throw new IllegalArgumentException("result answer and snapshot answer must match");
        }
        if (failure != null && runStatus != AgentRunStatus.FAILED) {
            throw new IllegalArgumentException("technical failure requires FAILED status");
        }
    }
}
