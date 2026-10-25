package com.kset.agent.core.loop;

import com.kset.agent.core.AgentCoreException;
import com.kset.agent.core.AgentErrorCode;

import java.time.Duration;

/**
 * Immutable run limits and per-invocation execution deadlines.
 * {@code maxPromptChars} counts UTF-16 code units in the prepared system and user prompts only.
 * {@code maxObservationSummaryChars} bounds one default ReAct model-visible summary, not the
 * original observation retained in the snapshot.
 */
public record AgentLoopOptions(
        int maxTurns,
        Duration invocationTimeout,
        int protocolErrorLimit,
        int noProgressLimit,
        int maxOutputTokens,
        Duration toolCallTimeout,
        Duration toolBatchTimeout,
        int maxActionsPerDecision,
        int maxToolCallsPerBatch,
        int maxPromptChars,
        int maxObservationSummaryChars) {

    public AgentLoopOptions(int maxTurns, Duration invocationTimeout,
                            int protocolErrorLimit, int noProgressLimit,
                            int maxOutputTokens, Duration toolCallTimeout,
                            Duration toolBatchTimeout, int maxActionsPerDecision,
                            int maxToolCallsPerBatch) {
        this(maxTurns, invocationTimeout, protocolErrorLimit, noProgressLimit,
                maxOutputTokens, toolCallTimeout, toolBatchTimeout,
                maxActionsPerDecision, maxToolCallsPerBatch, 32768, 4096);
    }

    public AgentLoopOptions {
        if (maxTurns < 1 || protocolErrorLimit < 1 || noProgressLimit < 1 || maxOutputTokens < 0
                || maxActionsPerDecision < 1 || maxToolCallsPerBatch < 2) {
            throw invalid("agent loop numeric limits are invalid");
        }
        if (maxPromptChars < 4096 || maxObservationSummaryChars < 2048
                || maxObservationSummaryChars > maxPromptChars / 2) {
            throw invalid("prompt limits require maxPromptChars >= 4096 and "
                    + "2048 <= maxObservationSummaryChars <= maxPromptChars / 2");
        }
        if (invocationTimeout == null || invocationTimeout.isZero()
                || invocationTimeout.isNegative()) {
            throw invalid("invocationTimeout must be positive");
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
                Duration.ofMinutes(2), Duration.ofMinutes(2), 8, 8, 32768, 4096);
    }

    private static AgentCoreException invalid(String errorMessage) {
        return new AgentCoreException(AgentErrorCode.INVALID_CONFIGURATION, errorMessage);
    }
}
