package com.kset.agent.core.stop;

/**
 * Thread-safe external cancellation probe checked around model and action calls. Sharing one
 * mutable probe across requests deliberately couples their cancellation and must be avoided.
 */
@FunctionalInterface
public interface AgentCancellation {

    boolean isCancellationRequested();

    static AgentCancellation none() {
        return () -> false;
    }
}
