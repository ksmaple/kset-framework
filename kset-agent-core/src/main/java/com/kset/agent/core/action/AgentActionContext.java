package com.kset.agent.core.action;

import com.kset.agent.core.api.AgentRequest;
import com.kset.agent.core.execution.AgentExecutionContext;
import com.kset.agent.core.loop.AgentRunState;

import java.time.Clock;
import java.util.Objects;

/** Immutable Agent request, run state and execution context supplied to one action handler. */
public record AgentActionContext(
        AgentRequest request,
        AgentRunState state,
        AgentExecutionContext executionContext,
        Clock clock) {

    public AgentActionContext {
        request = Objects.requireNonNull(request, "request");
        state = Objects.requireNonNull(state, "state");
        executionContext = Objects.requireNonNull(executionContext, "executionContext");
        clock = Objects.requireNonNull(clock, "clock");
    }
}
