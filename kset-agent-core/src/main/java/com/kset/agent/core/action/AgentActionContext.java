package com.kset.agent.core.action;

import com.kset.agent.core.api.AgentRequest;
import com.kset.agent.core.execution.AgentExecutionContext;
import com.kset.agent.core.loop.AgentRunState;

import java.time.Clock;
import java.util.Objects;

public record AgentActionContext(
        AgentRequest request,
        AgentRunState state,
        AgentExecutionContext execution,
        Clock clock) {

    public AgentActionContext {
        request = Objects.requireNonNull(request, "request");
        state = Objects.requireNonNull(state, "state");
        execution = Objects.requireNonNull(execution, "execution");
        clock = Objects.requireNonNull(clock, "clock");
    }
}
