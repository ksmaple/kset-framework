package com.kset.agent.core;

import java.util.Objects;

/** Base exception for failures raised by the agent kernel. */
public class AgentCoreException extends RuntimeException {

    private final AgentErrorCode code;
    private final boolean retryable;

    public AgentCoreException(AgentErrorCode code, String message) {
        this(code, message, false, null);
    }

    public AgentCoreException(AgentErrorCode code, String message, Throwable cause) {
        this(code, message, false, cause);
    }

    public AgentCoreException(
            AgentErrorCode code, String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.code = Objects.requireNonNull(code, "code");
        this.retryable = retryable;
    }

    public AgentErrorCode code() {
        return code;
    }

    public boolean retryable() {
        return retryable;
    }
}
