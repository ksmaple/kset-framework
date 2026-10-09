package com.kset.agent.core.api;

import com.kset.agent.core.loop.AgentRunSnapshot;
import com.kset.agent.core.stop.AgentStopDecision;

/** Terminal or suspended result returned by one synchronous loop invocation. */
public record AgentResult(
        String runId,
        AgentRunStatus status,
        String answer,
        AgentStopDecision stop,
        AgentRunSnapshot snapshot,
        AgentFailure failure) {
}
