package com.kset.agent.core.api;

import com.kset.agent.core.loop.AgentRunSnapshot;
import com.kset.agent.core.stop.AgentStopDecision;

/** Final or suspended result returned by the loop kernel. */
public record AgentResult(
        String runId,
        AgentRunStatus status,
        String answer,
        AgentStopDecision stop,
        AgentRunSnapshot snapshot) {
}
