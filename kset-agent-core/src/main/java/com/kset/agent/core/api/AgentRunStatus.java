package com.kset.agent.core.api;

/** Lifecycle status exposed by the stable loop API. */
public enum AgentRunStatus {
    RUNNING,
    SUSPENDED,
    COMPLETED,
    FAILED,
    CANCELLED
}
