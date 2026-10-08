package com.kset.agent.core.action;

import java.util.Map;

/** A protocol-neutral action selected by the model. */
public interface AgentAction {

    String type();

    default Map<String, Object> metadata() {
        return Map.of();
    }
}
