package com.kset.agent.core.execution;

import com.kset.agent.core.event.AgentLifecycleStepType;
import com.kset.agent.core.stop.AgentCancellation;
import com.kset.agent.core.value.AgentValueSnapshot;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** Immutable trace, cancellation and timing context for one active kernel operation. */
public record AgentExecutionContext(
        String agentRunId,
        String invocationId,
        String stepId,
        String parentStepId,
        AgentLifecycleStepType stepType,
        String operation,
        int turn,
        Instant executionStartedAt,
        Instant issuedAt,
        Instant deadline,
        AgentCancellation cancellation,
        Map<String, Object> attributes) {

    public AgentExecutionContext {
        if (agentRunId == null || agentRunId.isBlank()) {
            throw new IllegalArgumentException("agentRunId must not be blank");
        }
        if (invocationId == null || invocationId.isBlank()) {
            throw new IllegalArgumentException("invocationId must not be blank");
        }
        if (stepId == null || stepId.isBlank()) {
            throw new IllegalArgumentException("stepId must not be blank");
        }
        parentStepId = parentStepId == null || parentStepId.isBlank()
                ? null : parentStepId.trim();
        stepType = Objects.requireNonNull(stepType, "stepType");
        if (operation == null || operation.isBlank()) {
            throw new IllegalArgumentException("operation must not be blank");
        }
        agentRunId = agentRunId.trim();
        invocationId = invocationId.trim();
        stepId = stepId.trim();
        operation = operation.trim();
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
        attributes = AgentValueSnapshot.map(attributes);
    }

    public boolean isCancellationRequested() {
        return cancellation.isCancellationRequested() || Thread.currentThread().isInterrupted();
    }

    public Duration remainingFrom(Instant now) {
        Objects.requireNonNull(now, "now");
        return now.isBefore(deadline) ? Duration.between(now, deadline) : Duration.ZERO;
    }
}
