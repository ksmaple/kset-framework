package com.kset.agent.core.protocol;

/** Protocol input exceeded a configured run capacity before action materialization. */
public final class AgentProtocolCapacityException extends AgentProtocolException {

    public AgentProtocolCapacityException(String message) {
        super("CAPACITY_LIMIT", message);
    }
}
