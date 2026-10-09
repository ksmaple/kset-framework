package com.kset.agent.core.event;

import com.kset.agent.core.action.AgentAction;
import com.kset.agent.core.action.AgentActionResult;
import com.kset.agent.core.api.AgentRequest;
import com.kset.agent.core.loop.AgentRunState;
import com.kset.agent.core.model.ModelResponse;
import com.kset.agent.core.protocol.AgentDecision;
import com.kset.agent.core.protocol.AgentProtocolException;
import com.kset.agent.core.stop.AgentStopDecision;

/**
 * Thread-safe, read-only lifecycle observer. Listeners cannot override loop or stop decisions.
 */
public interface AgentLifecycleListener {

    default boolean critical() { return false; }
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
