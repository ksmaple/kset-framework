package com.kset.agent.core.stop;

import com.kset.agent.core.api.AgentRunStatus;

import java.util.Objects;

/** Authoritative decision describing whether, why and in which run status a loop stops. */
public record AgentStopDecision(
        boolean shouldStop,
        AgentStopReason stopReason,
        AgentRunStatus runStatus,
        String stopMessage) {

    public AgentStopDecision {
        stopReason = Objects.requireNonNull(stopReason, "stopReason");
        runStatus = Objects.requireNonNull(runStatus, "runStatus");
        stopMessage = stopMessage == null ? "" : stopMessage;
        if (!shouldStop
                && (stopReason != AgentStopReason.NONE
                || runStatus != AgentRunStatus.RUNNING)) {
            throw new IllegalArgumentException("continuation decision must use NONE and RUNNING");
        }
        if (shouldStop
                && (stopReason == AgentStopReason.NONE
                || runStatus == AgentRunStatus.RUNNING)) {
            throw new IllegalArgumentException("stop decision requires a reason and terminal status");
        }
    }

    public static AgentStopDecision continueRun() {
        return new AgentStopDecision(false, AgentStopReason.NONE, AgentRunStatus.RUNNING, "");
    }

    public static AgentStopDecision stop(
            AgentStopReason stopReason, AgentRunStatus runStatus, String stopMessage) {
        return new AgentStopDecision(true, stopReason, runStatus, stopMessage);
    }
}
