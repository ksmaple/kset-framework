package com.kset.agent.core.protocol;

import com.kset.agent.core.api.AgentRequest;
import com.kset.agent.core.loop.AgentRunState;

public record AgentProtocolContext(AgentRequest request, AgentRunState state) {
}
