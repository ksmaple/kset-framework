package com.kset.agent.core.action;

import com.kset.agent.core.api.AgentRequest;
import com.kset.agent.core.loop.AgentRunState;

public record AgentActionContext(AgentRequest request, AgentRunState state) {
}
