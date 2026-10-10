package com.kset.agent.core.api;

import com.kset.agent.core.AgentErrorCode;

import java.util.Objects;

/** Stable technical error code and safe message returned for an active-loop failure. */
public record AgentFailure(
        AgentErrorCode errorCode,
        String errorMessage,
        boolean retryable) {

    public AgentFailure {
        errorCode = Objects.requireNonNull(errorCode, "errorCode");
        errorMessage = errorMessage == null || errorMessage.isBlank()
                ? errorCode.name() : errorMessage;
    }
}
