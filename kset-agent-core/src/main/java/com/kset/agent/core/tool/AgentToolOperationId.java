package com.kset.agent.core.tool;

/** Structural idempotency identity for one tool operation within an agent run. */
public record AgentToolOperationId(String runId, String callId) {

    public AgentToolOperationId {
        if (runId == null || runId.isBlank()) {
            throw new IllegalArgumentException("runId must not be blank");
        }
        if (callId == null || callId.isBlank()) {
            throw new IllegalArgumentException("callId must not be blank");
        }
        runId = runId.trim();
        callId = callId.trim();
    }
}
