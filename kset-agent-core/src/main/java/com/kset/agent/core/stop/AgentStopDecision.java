package com.kset.agent.core.stop;

import com.kset.agent.core.api.AgentRunStatus;

import java.util.Objects;

/** Authoritative decision describing whether and why a loop must stop. */
public record AgentStopDecision(
        boolean stop,
        AgentStopReason reason,
        AgentRunStatus status,
        String detail) {

    public AgentStopDecision {
        reason = Objects.requireNonNull(reason, "reason");
        status = Objects.requireNonNull(status, "status");
        detail = detail == null ? "" : detail;
        if (!stop && (reason != AgentStopReason.NONE || status != AgentRunStatus.RUNNING)) {
            throw new IllegalArgumentException("continuation decision must use NONE and RUNNING");
        }
        if (stop && (reason == AgentStopReason.NONE || status == AgentRunStatus.RUNNING)) {
            throw new IllegalArgumentException("stop decision requires a reason and terminal status");
        }
    }

    public static AgentStopDecision continueRun() {
        return new AgentStopDecision(false, AgentStopReason.NONE, AgentRunStatus.RUNNING, "");
    }

    public static AgentStopDecision stop(
            AgentStopReason reason, AgentRunStatus status, String detail) {
        return new AgentStopDecision(true, reason, status, detail);
    }
}
