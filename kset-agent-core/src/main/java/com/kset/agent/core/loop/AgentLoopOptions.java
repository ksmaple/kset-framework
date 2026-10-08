package com.kset.agent.core.loop;

import java.time.Duration;

/** Immutable safety and capacity limits for one agent run. */
public record AgentLoopOptions(
        int maxTurns,
        Duration timeout,
        int protocolErrorLimit,
        int noProgressLimit,
        int maxOutputTokens,
        Duration toolBatchTimeout) {

    public AgentLoopOptions {
        if (maxTurns < 1 || protocolErrorLimit < 1 || noProgressLimit < 1 || maxOutputTokens < 0) {
            throw new IllegalArgumentException("agent loop numeric limits are invalid");
        }
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        if (toolBatchTimeout == null || toolBatchTimeout.isZero() || toolBatchTimeout.isNegative()) {
            throw new IllegalArgumentException("toolBatchTimeout must be positive");
        }
    }

    public static AgentLoopOptions defaults() {
        return new AgentLoopOptions(20, Duration.ofMinutes(5), 3, 3, 0, Duration.ofMinutes(2));
    }
}
