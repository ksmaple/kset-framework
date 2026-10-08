package com.kset.agent.core.stop;

/** External cancellation probe checked before every model call and after every action. */
@FunctionalInterface
public interface AgentCancellation {

    boolean isCancellationRequested();

    static AgentCancellation none() {
        return () -> false;
    }
}
