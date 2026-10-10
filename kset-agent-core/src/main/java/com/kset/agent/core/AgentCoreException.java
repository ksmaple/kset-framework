package com.kset.agent.core;

import java.util.Objects;

/** Base exception for failures raised by the agent kernel. */
public class AgentCoreException extends RuntimeException {

    private final AgentErrorCode errorCode;
    private final boolean retryable;

    public AgentCoreException(AgentErrorCode errorCode, String errorMessage) {
        this(errorCode, errorMessage, false, null);
    }

    public AgentCoreException(
            AgentErrorCode errorCode, String errorMessage, Throwable cause) {
        this(errorCode, errorMessage, false, cause);
    }

    public AgentCoreException(
            AgentErrorCode errorCode, String errorMessage,
            boolean retryable, Throwable cause) {
        super(errorMessage, cause);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
        this.retryable = retryable;
    }

    public AgentErrorCode errorCode() {
        return errorCode;
    }

    public boolean retryable() {
        return retryable;
    }
}
