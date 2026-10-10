package com.kset.agent.core.api;

/** Stable execution status of an agent run, distinct from per-event lifecycle status. */
public enum AgentRunStatus {
    RUNNING,
    SUSPENDED,
    COMPLETED,
    FAILED,
    CANCELLED
}
