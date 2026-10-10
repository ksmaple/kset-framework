package com.kset.agent.core.model;

import com.kset.agent.core.AgentCoreException;
import com.kset.agent.core.AgentErrorCode;

import java.time.Duration;

/** Immutable limits for retryable model-call failures within one active invocation. */
public record AgentModelRetryOptions(
        int maxAttempts, Duration initialDelay, Duration maxDelay) {

    public AgentModelRetryOptions {
        if (maxAttempts < 1 || maxAttempts > 10) {
            throw invalid("model retry maxAttempts must be between 1 and 10");
        }
        if (initialDelay == null || initialDelay.compareTo(Duration.ofMillis(1)) < 0
                || maxDelay == null || maxDelay.compareTo(initialDelay) < 0
                || maxDelay.compareTo(Duration.ofSeconds(30)) > 0) {
            throw invalid("model retry delays must be between 1 ms and 30 s, with maxDelay >= initialDelay");
        }
    }

    public static AgentModelRetryOptions defaults() {
        return new AgentModelRetryOptions(3, Duration.ofMillis(100), Duration.ofSeconds(1));
    }

    private static AgentCoreException invalid(String errorMessage) {
        return new AgentCoreException(AgentErrorCode.INVALID_CONFIGURATION, errorMessage);
    }
}
