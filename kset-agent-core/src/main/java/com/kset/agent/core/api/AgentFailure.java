package com.kset.agent.core.api;

import com.kset.agent.core.AgentErrorCode;

import java.util.Objects;

/** Structured technical failure returned when an active loop execution fails. */
public record AgentFailure(
        AgentErrorCode code,
        String message,
        boolean retryable) {

    public AgentFailure {
        code = Objects.requireNonNull(code, "code");
        message = message == null || message.isBlank() ? code.name() : message;
    }
}
