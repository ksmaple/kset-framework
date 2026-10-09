package com.kset.agent.core.strategy;

import com.kset.agent.core.action.AgentActionResult;
import com.kset.agent.core.api.AgentRequest;
import com.kset.agent.core.loop.AgentRunState;
import com.kset.agent.core.protocol.AgentDecision;

import java.time.Instant;
import java.util.List;

/**
 * Decides how a loop turn is prompted and which semantic decisions are valid.
 * Implementations shared by a kernel must be thread-safe. {@link #afterTurn} may update only
 * attributes under the namespace returned by {@link #id()} and must preserve all kernel-owned
 * state fields.
 */
public interface AgentReasoningStrategy {

    String id();

    AgentTurn nextTurn(AgentRequest request, AgentRunState state);

    default void validate(AgentRequest request, AgentRunState state, AgentDecision decision) {
    }

    default AgentRunState afterTurn(AgentRequest request, AgentRunState state,
                                    AgentDecision decision, List<AgentActionResult> results,
                                    Instant now) {
        return state;
    }
}
