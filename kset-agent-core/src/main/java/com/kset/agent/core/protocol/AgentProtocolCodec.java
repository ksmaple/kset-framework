package com.kset.agent.core.protocol;

import com.kset.agent.core.model.ModelRequest;
import com.kset.agent.core.model.ModelResponse;

/** Converts one model protocol into the stable decision model. Implementations must be thread-safe. */
public interface AgentProtocolCodec {

    AgentProtocolId protocolId();

    default ModelRequest prepare(ModelRequest request, AgentProtocolContext context) {
        return request;
    }

    AgentDecision decode(ModelResponse response, AgentProtocolContext context);
}
