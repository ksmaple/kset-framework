package com.kset.agent.core.execution;

import com.kset.agent.core.stop.AgentCancellation;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Immutable context for one active invocation of an agent run. */
public record AgentExecutionContext(
        String runId,
        int turn,
        Instant executionStartedAt,
        Instant issuedAt,
        Instant deadline,
        AgentCancellation cancellation,
        Map<String, Object> attributes) {

    public AgentExecutionContext {
        if (runId == null || runId.isBlank()) {
            throw new IllegalArgumentException("runId must not be blank");
        }
        if (turn < 0) {
            throw new IllegalArgumentException("turn must not be negative");
        }
        executionStartedAt = Objects.requireNonNull(executionStartedAt, "executionStartedAt");
        issuedAt = Objects.requireNonNull(issuedAt, "issuedAt");
        deadline = Objects.requireNonNull(deadline, "deadline");
        if (issuedAt.isBefore(executionStartedAt)) {
            throw new IllegalArgumentException("issuedAt must not be before executionStartedAt");
        }
        if (deadline.isBefore(executionStartedAt)) {
            throw new IllegalArgumentException("deadline must not be before executionStartedAt");
        }
        cancellation = cancellation == null ? AgentCancellation.none() : cancellation;
        attributes = attributes == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(attributes));
    }

    public boolean isCancellationRequested() {
        return cancellation.isCancellationRequested() || Thread.currentThread().isInterrupted();
    }

    public Duration remainingFrom(Instant now) {
        Objects.requireNonNull(now, "now");
        return now.isBefore(deadline) ? Duration.between(now, deadline) : Duration.ZERO;
    }
}
