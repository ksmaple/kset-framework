package com.kset.agent.core.event;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Stable identity and timing envelope attached to every lifecycle callback. */
public record AgentLifecycleContext(
        int version,
        AgentLifecycleEventType eventType,
        AgentInvocationType invocationType,
        String invocationId,
        long sequence,
        String runId,
        int turn,
        int actionIndex,
        Instant occurredAt,
        Instant executionStartedAt,
        Instant deadline,
        Duration elapsed) {

    public static final int CURRENT_VERSION = 1;
    public static final int NO_ACTION = -1;

    public AgentLifecycleContext {
        if (version != CURRENT_VERSION) {
            throw new IllegalArgumentException("unsupported lifecycle context version: " + version);
        }
        eventType = Objects.requireNonNull(eventType, "eventType");
        invocationType = Objects.requireNonNull(invocationType, "invocationType");
        if (invocationId == null || invocationId.isBlank()) {
            throw new IllegalArgumentException("invocationId must not be blank");
        }
        if (sequence < 1L) {
            throw new IllegalArgumentException("lifecycle sequence must be positive");
        }
        if (runId == null || runId.isBlank()) {
            throw new IllegalArgumentException("runId must not be blank");
        }
        if (turn < 0 || actionIndex < NO_ACTION) {
            throw new IllegalArgumentException("turn or actionIndex is invalid");
        }
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        executionStartedAt = Objects.requireNonNull(executionStartedAt, "executionStartedAt");
        deadline = Objects.requireNonNull(deadline, "deadline");
        elapsed = Objects.requireNonNull(elapsed, "elapsed");
        if (deadline.isBefore(executionStartedAt) || elapsed.isNegative()) {
            throw new IllegalArgumentException("lifecycle timing is invalid");
        }
    }

    /** Unique within the process lifetime and stable for this emitted event. */
    public String eventId() {
        return invocationId + ":" + sequence;
    }

    public boolean actionScoped() {
        return actionIndex != NO_ACTION;
    }
}
