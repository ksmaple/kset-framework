package com.kset.agent.core.tool;

/** Structural idempotency key for one tool operation within an Agent run. */
public record AgentToolIdempotencyKey(String agentRunId, String callId) {

    public AgentToolIdempotencyKey {
        if (agentRunId == null || agentRunId.isBlank()) {
            throw new IllegalArgumentException("agentRunId must not be blank");
        }
        if (callId == null || callId.isBlank()) {
            throw new IllegalArgumentException("callId must not be blank");
        }
        agentRunId = agentRunId.trim();
        callId = callId.trim();
    }
}
