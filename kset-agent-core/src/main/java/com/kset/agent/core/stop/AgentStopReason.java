package com.kset.agent.core.stop;

/** Stable reason codes for every loop exit. */
public enum AgentStopReason {
    NONE,
    COMPLETED,
    WAITING_INPUT,
    USER_CANCELLED,
    THREAD_INTERRUPTED,
    TIMEOUT,
    MAX_TURNS,
    PROTOCOL_ERROR_LIMIT,
    NO_PROGRESS_LIMIT,
    FATAL_ERROR,
    POLICY
}
