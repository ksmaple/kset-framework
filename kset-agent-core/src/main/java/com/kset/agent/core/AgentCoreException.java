package com.kset.agent.core;

/** Base exception for failures raised by the agent kernel. */
public class AgentCoreException extends RuntimeException {

    public AgentCoreException(String message) {
        super(message);
    }

    public AgentCoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
