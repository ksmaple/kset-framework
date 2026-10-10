package com.kset.agent.core.api;

import com.kset.agent.core.loop.AgentRunSnapshot;
import com.kset.agent.core.stop.AgentStopDecision;

/**
 * Immutable result of one synchronous run or resume invocation.
 * The run status and stop decision always describe the same terminal or suspended state.
 */
public record AgentResult(
        String runId,
        AgentRunStatus runStatus,
        String answer,
        AgentStopDecision stopDecision,
        AgentRunSnapshot snapshot,
        AgentFailure failure) {
}
