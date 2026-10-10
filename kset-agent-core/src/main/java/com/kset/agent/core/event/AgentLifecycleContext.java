package com.kset.agent.core.event;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Stable Agent identity, eventSequence and timing envelope for every lifecycle callback. */
public record AgentLifecycleContext(
        int version,
        AgentLifecycleEventType eventType,
        AgentInvocationType invocationType,
        String invocationId,
        long eventSequence,
        String runId,
        String stepId,
        String parentStepId,
        AgentLifecycleStepType stepType,
        String operation,
        int turn,
        int actionIndex,
        Instant occurredAt,
        Instant executionStartedAt,
        Instant deadline,
        Duration elapsed) {

    public static final int CURRENT_VERSION = 2;
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
        if (eventSequence < 1L) {
            throw new IllegalArgumentException("eventSequence must be positive");
        }
        if (runId == null || runId.isBlank()) {
            throw new IllegalArgumentException("runId must not be blank");
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
        stepId = stepId.trim();
        operation = operation.trim();
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
        return invocationId + ":" + eventSequence;
    }

    public boolean actionScoped() {
        return actionIndex != NO_ACTION;
    }

    /** Derives the per-event logging status; callers must not treat it as an AgentRunStatus. */
    public AgentLifecycleStatus status() {
        return switch (eventType) {
            case RUN_STARTED, TURN_STARTED, MODEL_STARTED, DECISION_STARTED,
                    ACTION_STARTED, CHECKPOINT_SAVING ->
                    AgentLifecycleStatus.STARTED;
            case MODEL_COMPLETED, DECISION_ACCEPTED, ACTION_COMPLETED, TURN_COMPLETED,
                    CHECKPOINT_SAVED, RUN_RETURNED -> AgentLifecycleStatus.COMPLETED;
            case MODEL_FAILED, DECISION_FAILED, ACTION_FAILED, TURN_FAILED,
                    PROTOCOL_ERROR, CHECKPOINT_FAILED, RUN_FAILED ->
                    AgentLifecycleStatus.FAILED;
            case RUN_STOPPED -> AgentLifecycleStatus.STOPPED;
        };
    }
}
