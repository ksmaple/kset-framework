package com.kset.agent.core.event;

/** Stable logical step categories used to group lifecycle events. */
public enum AgentLifecycleStepType {
    RUN,
    TURN,
    MODEL,
    DECISION,
    ACTION,
    CHECKPOINT
}
