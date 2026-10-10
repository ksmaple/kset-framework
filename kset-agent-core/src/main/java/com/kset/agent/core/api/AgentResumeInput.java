package com.kset.agent.core.api;

import com.kset.agent.core.AgentCoreException;
import com.kset.agent.core.AgentErrorCode;
import com.kset.agent.core.action.AgentObservation;

import java.util.List;

/**
 * Authoritative external results used to resume a suspended reconciliation. Build entries with
 * {@link AgentObservation#toolResult(String, String, String,
 * com.kset.agent.core.tool.ToolExecutionResult)}.
 */
public record AgentResumeInput(List<AgentObservation> reconciledObservations) {

    public AgentResumeInput {
        try {
            reconciledObservations = reconciledObservations == null
                    ? List.of() : List.copyOf(reconciledObservations);
        } catch (RuntimeException error) {
            throw new AgentCoreException(AgentErrorCode.INVALID_RESUME_INPUT,
                    "reconciled observations must not contain null", error);
        }
    }

    public static AgentResumeInput none() {
        return new AgentResumeInput(List.of());
    }

    public boolean hasReconciledObservations() {
        return !reconciledObservations.isEmpty();
    }
}
