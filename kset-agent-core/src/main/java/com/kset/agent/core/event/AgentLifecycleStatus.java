package com.kset.agent.core.event;

/** Stable per-event status for step logging; this is not an Agent run status. */
public enum AgentLifecycleStatus {
    STARTED,
    COMPLETED,
    FAILED,
    STOPPED
}
