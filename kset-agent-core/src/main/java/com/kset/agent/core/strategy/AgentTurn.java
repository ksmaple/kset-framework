package com.kset.agent.core.strategy;

import com.kset.agent.core.model.ModelRequest;
import com.kset.agent.core.protocol.AgentProtocolId;

import java.util.Objects;

/** Model request and protocol selected for one reasoning turn. */
public record AgentTurn(AgentProtocolId protocol, ModelRequest modelRequest) {

    public AgentTurn {
        Objects.requireNonNull(protocol, "protocol");
        Objects.requireNonNull(modelRequest, "modelRequest");
    }
}
