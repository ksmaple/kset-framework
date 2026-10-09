package com.kset.agent.core.event;

/** Stable event types emitted by one synchronous run or resume invocation. */
public enum AgentLifecycleEventType {
    RUN_STARTED,
    TURN_STARTED,
    MODEL_STARTED,
    MODEL_COMPLETED,
    DECISION_ACCEPTED,
    ACTION_STARTED,
    ACTION_COMPLETED,
    TURN_COMPLETED,
    PROTOCOL_ERROR,
    CHECKPOINT_SAVING,
    CHECKPOINT_SAVED,
    RUN_FAILED,
    RUN_STOPPED,
    RUN_RETURNED
}
