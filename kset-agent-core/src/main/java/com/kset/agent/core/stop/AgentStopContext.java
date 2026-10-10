package com.kset.agent.core.stop;

import com.kset.agent.core.api.AgentRequest;
import com.kset.agent.core.execution.AgentExecutionContext;
import com.kset.agent.core.loop.AgentRunState;

import java.time.Instant;

/** Read-only Agent state and execution context supplied to one stop-policy evaluation. */
public record AgentStopContext(
        AgentRequest request,
        AgentRunState state,
        AgentExecutionContext executionContext,
        Instant now) {
}
