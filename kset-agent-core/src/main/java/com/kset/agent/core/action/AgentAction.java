package com.kset.agent.core.action;

import java.util.Map;

/** A protocol-neutral action selected by the model. */
public interface AgentAction {

    /** Stable protocol-neutral action discriminator. */
    String actionType();

    default Map<String, Object> metadata() {
        return Map.of();
    }
}
