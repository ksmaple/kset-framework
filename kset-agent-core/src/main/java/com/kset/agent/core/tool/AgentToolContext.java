package com.kset.agent.core.tool;

import com.kset.agent.core.execution.AgentExecutionContext;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Tool identity plus the immutable invocation and action-step execution context. */
public record AgentToolContext(
        AgentExecutionContext executionContext,
        String callId,
        String taskId,
        String toolName,
        Instant deadline) {

    public AgentToolContext {
        executionContext = Objects.requireNonNull(executionContext, "executionContext");
        if (callId == null || callId.isBlank()) {
            throw new IllegalArgumentException("callId must not be blank");
        }
        if (toolName == null || toolName.isBlank()) {
            throw new IllegalArgumentException("toolName must not be blank");
        }
        callId = callId.trim();
        taskId = taskId == null || taskId.isBlank() ? null : taskId.trim();
        toolName = toolName.trim();
        deadline = Objects.requireNonNull(deadline, "deadline");
        if (deadline.isAfter(executionContext.deadline())) {
            throw new IllegalArgumentException("tool deadline must not exceed execution deadline");
        }
    }

    public boolean isCancellationRequested() {
        return executionContext.isCancellationRequested();
    }

    /** Stable structural idempotency key; never persist {@code callId} by itself. */
    public AgentToolIdempotencyKey idempotencyKey() {
        return new AgentToolIdempotencyKey(executionContext.agentRunId(), callId);
    }

    public boolean isDeadlineExceeded(Instant now) {
        Objects.requireNonNull(now, "now");
        return !now.isBefore(deadline);
    }

    public Duration remainingFrom(Instant now) {
        return isDeadlineExceeded(now) ? Duration.ZERO : Duration.between(now, deadline);
    }
}
