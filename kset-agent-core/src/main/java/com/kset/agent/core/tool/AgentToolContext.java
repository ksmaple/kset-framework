package com.kset.agent.core.tool;

import com.kset.agent.core.execution.AgentExecutionContext;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Identity, cancellation and deadline context for one tool call. */
public record AgentToolContext(
        AgentExecutionContext execution,
        String callId,
        String taskId,
        String toolName,
        Instant deadline) {

    public AgentToolContext {
        execution = Objects.requireNonNull(execution, "execution");
        if (callId == null || callId.isBlank()) {
            throw new IllegalArgumentException("callId must not be blank");
        }
        if (toolName == null || toolName.isBlank()) {
            throw new IllegalArgumentException("toolName must not be blank");
        }
        deadline = Objects.requireNonNull(deadline, "deadline");
        if (deadline.isAfter(execution.deadline())) {
            throw new IllegalArgumentException("tool deadline must not exceed execution deadline");
        }
    }

    public boolean isCancellationRequested() {
        return execution.isCancellationRequested();
    }

    public boolean isDeadlineExceeded(Instant now) {
        Objects.requireNonNull(now, "now");
        return !now.isBefore(deadline);
    }

    public Duration remainingFrom(Instant now) {
        return isDeadlineExceeded(now) ? Duration.ZERO : Duration.between(now, deadline);
    }
}
