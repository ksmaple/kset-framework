package com.kset.agent.core.event;

import com.kset.agent.core.action.AgentAction;
import com.kset.agent.core.action.AgentActionResult;
import com.kset.agent.core.api.AgentRequest;
import com.kset.agent.core.api.AgentResult;
import com.kset.agent.core.loop.AgentRunSnapshot;
import com.kset.agent.core.loop.AgentRunState;
import com.kset.agent.core.model.ModelRequest;
import com.kset.agent.core.model.ModelResponse;
import com.kset.agent.core.protocol.AgentDecision;
import com.kset.agent.core.protocol.AgentProtocolException;
import com.kset.agent.core.stop.AgentStopDecision;

import java.util.List;

/**
 * Thread-safe, read-only lifecycle observer. Every callback with a lifecycle context has a stable
 * event type, run/invocation/step/event identities, operation and timing envelope. Listeners cannot
 * override loop or stop decisions. A critical listener can fail active-loop callbacks, but
 * terminal callbacks are always best-effort because the returned result has already been decided.
 */
public interface AgentLifecycleListener {

    /** Applies only to callbacks invoked while the loop can still advance. */
    default boolean critical() { return false; }

    /** Receives every lifecycle event and is the preferred endpoint for complete step logs. */
    default void onEvent(AgentLifecycleContext context) { }

    default void beforeRun(AgentLifecycleContext context,
                           AgentRequest request, AgentRunState state) {
        beforeRun(request, state);
    }

    default void beforeTurn(AgentLifecycleContext context,
                            AgentRequest request, AgentRunState state) {
        beforeTurn(request, state);
    }

    default void beforeModel(AgentLifecycleContext context, AgentRequest request,
                             AgentRunState state, ModelRequest modelRequest) { }

    default void afterModel(AgentLifecycleContext context, AgentRequest request,
                            AgentRunState state, ModelResponse response) {
        afterModel(request, state, response);
    }

    default void beforeDecision(AgentLifecycleContext context, AgentRequest request,
                                AgentRunState state, ModelResponse response) { }

    default void afterDecision(AgentLifecycleContext context, AgentRequest request,
                               AgentRunState state, AgentDecision decision) {
        afterDecision(request, state, decision);
    }

    default void beforeAction(AgentLifecycleContext context, AgentRequest request,
                              AgentRunState state, AgentAction action) { }

    default void afterAction(AgentLifecycleContext context, AgentRequest request,
                             AgentRunState state, AgentAction action, AgentActionResult result) {
        afterAction(request, state, action, result);
    }

    default void afterTurn(AgentLifecycleContext context, AgentRequest request,
                           AgentRunState state, AgentDecision decision,
                           List<AgentActionResult> results) { }

    default void onTurnError(AgentLifecycleContext context, AgentRequest request,
                             AgentRunState state, RuntimeException error) { }

    default void onProtocolError(AgentLifecycleContext context, AgentRequest request,
                                 AgentRunState state, AgentProtocolException error) {
        onProtocolError(request, state, error);
    }

    default void beforeCheckpoint(AgentLifecycleContext context, AgentRequest request,
                                  AgentRunSnapshot snapshot) { }

    default void afterCheckpoint(AgentLifecycleContext context, AgentRequest request,
                                 AgentRunSnapshot snapshot) { }

    /** Best-effort notification that cannot replace the checkpoint failure being reported. */
    default void onCheckpointError(AgentLifecycleContext context, AgentRequest request,
                                   AgentRunSnapshot snapshot, RuntimeException error) { }

    /** Best-effort terminal notification; exceptions are ignored by the kernel. */
    default void onStop(AgentLifecycleContext context, AgentRequest request,
                        AgentRunState state, AgentStopDecision decision) {
        onStop(request, state, decision);
    }

    /** Best-effort terminal notification; exceptions are ignored by the kernel. */
    default void onError(AgentLifecycleContext context, AgentRequest request,
                         AgentRunState state, RuntimeException error) {
        onError(request, state, error);
    }

    /** Best-effort final result notification; exceptions are ignored by the kernel. */
    default void afterRun(AgentLifecycleContext context,
                          AgentRequest request, AgentResult result) { }

    /** Best-effort notification sent to other listeners when one listener callback fails. */
    default void onListenerError(AgentLifecycleContext failedEvent, String listenerType,
                                 boolean critical, RuntimeException error) { }

    // Compatibility callbacks remain available for existing listeners.
    default void beforeRun(AgentRequest request, AgentRunState state) { }
    default void beforeTurn(AgentRequest request, AgentRunState state) { }
    default void afterModel(AgentRequest request, AgentRunState state, ModelResponse response) { }
    default void afterDecision(AgentRequest request, AgentRunState state, AgentDecision decision) { }
    default void afterAction(AgentRequest request, AgentRunState state,
                             AgentAction action, AgentActionResult result) { }
    default void onProtocolError(AgentRequest request, AgentRunState state,
                                 AgentProtocolException error) { }
    default void onStop(AgentRequest request, AgentRunState state, AgentStopDecision decision) { }

    default void onError(AgentRequest request, AgentRunState state, RuntimeException error) { }
}
