package com.kset.agent.core.loop;

import com.kset.agent.core.AgentCoreException;
import com.kset.agent.core.AgentErrorCode;

import java.time.Duration;

/** Immutable safety and capacity limits for one agent run. */
public record AgentLoopOptions(
        int maxTurns,
        Duration timeout,
        int protocolErrorLimit,
        int noProgressLimit,
        int maxOutputTokens,
        Duration toolCallTimeout,
        Duration toolBatchTimeout,
        int maxActionsPerDecision,
        int maxToolCallsPerBatch) {

    public AgentLoopOptions {
        if (maxTurns < 1 || protocolErrorLimit < 1 || noProgressLimit < 1 || maxOutputTokens < 0
                || maxActionsPerDecision < 1 || maxToolCallsPerBatch < 2) {
            throw invalid("agent loop numeric limits are invalid");
        }
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw invalid("timeout must be positive");
        }
        if (toolCallTimeout == null || toolCallTimeout.isZero() || toolCallTimeout.isNegative()) {
            throw invalid("toolCallTimeout must be positive");
        }
        if (toolBatchTimeout == null || toolBatchTimeout.isZero() || toolBatchTimeout.isNegative()) {
            throw invalid("toolBatchTimeout must be positive");
        }
    }

    public static AgentLoopOptions defaults() {
        return new AgentLoopOptions(20, Duration.ofMinutes(5), 3, 3, 0,
                Duration.ofMinutes(2), Duration.ofMinutes(2), 8, 8);
    }

    private static AgentCoreException invalid(String errorMessage) {
        return new AgentCoreException(AgentErrorCode.INVALID_CONFIGURATION, errorMessage);
    }
}
