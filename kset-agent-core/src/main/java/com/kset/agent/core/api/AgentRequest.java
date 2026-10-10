package com.kset.agent.core.api;

import com.kset.agent.core.AgentCoreException;
import com.kset.agent.core.AgentErrorCode;
import com.kset.agent.core.loop.AgentLoopOptions;
import com.kset.agent.core.protocol.AgentProtocolId;
import com.kset.agent.core.stop.AgentCancellation;
import com.kset.agent.core.value.AgentValueSnapshot;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;

/**
 * Input to a new or resumed Agent run. A new request may omit agentRunId when the kernel has an ID
 * generator; resume requests must provide the snapshot agentRunId. Structured attribute containers
 * are recursively snapshotted; opaque values must be immutable or scoped to this request.
 */
public record AgentRequest(
        String agentRunId,
        String task,
        AgentProtocolId protocolId,
        AgentLoopOptions options,
        Map<String, Object> attributes,
        AgentCancellation cancellation) {

    public static final String APPROVED_CONFIRMATION_IDS = "agent.approvedConfirmationIds";
    public static final String APPROVED_TOOL_CALL_IDS = "agent.approvedToolCallIds";
    private static final String CORE_ATTRIBUTE_PREFIX = "agent.";
    private static final String REACT_ATTRIBUTE_PREFIX = "react.";

    public AgentRequest {
        agentRunId = agentRunId == null || agentRunId.isBlank() ? null : agentRunId.trim();
        if (task == null || task.isBlank()) {
            throw new AgentCoreException(AgentErrorCode.INVALID_REQUEST,
                    "task must not be blank");
        }
        task = task.trim();
        if (protocolId == null) {
            throw new AgentCoreException(AgentErrorCode.INVALID_REQUEST,
                    "protocolId must not be null");
        }
        options = options == null ? AgentLoopOptions.defaults() : options;
        try {
            attributes = AgentValueSnapshot.map(attributes);
        } catch (RuntimeException error) {
            throw new AgentCoreException(AgentErrorCode.INVALID_REQUEST,
                    "request attributes must not contain null keys or values", error);
        }
        for (String key : attributes.keySet()) {
            if (isReservedAttribute(key) && !APPROVED_CONFIRMATION_IDS.equals(key)
                    && !APPROVED_TOOL_CALL_IDS.equals(key)) {
                throw new AgentCoreException(AgentErrorCode.INVALID_REQUEST,
                        "request attribute uses a reserved key: " + key);
            }
        }
        cancellation = cancellation == null ? AgentCancellation.none() : cancellation;
    }

    public static AgentRequest of(String task, AgentProtocolId protocolId) {
        return new AgentRequest(null, task, protocolId, AgentLoopOptions.defaults(), Map.of(),
                AgentCancellation.none());
    }

    public static AgentRequest of(
            String agentRunId, String task, AgentProtocolId protocolId) {
        return new AgentRequest(agentRunId, task, protocolId, AgentLoopOptions.defaults(), Map.of(),
                AgentCancellation.none());
    }

    public boolean hasAgentRunId() {
        return agentRunId != null;
    }

    public AgentRequest withAgentRunId(String agentRunId) {
        if (agentRunId == null || agentRunId.isBlank()) {
            throw new AgentCoreException(AgentErrorCode.INVALID_REQUEST,
                    "agentRunId must not be blank");
        }
        return new AgentRequest(agentRunId, task, protocolId, options, attributes, cancellation);
    }

    public boolean isConfirmationApproved(String confirmationId) {
        return containsApprovedId(APPROVED_CONFIRMATION_IDS, confirmationId);
    }

    public boolean isToolCallApproved(String callId) {
        return containsApprovedId(APPROVED_TOOL_CALL_IDS, callId);
    }

    private boolean containsApprovedId(String attributeKey, String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        Object value = attributes.get(attributeKey);
        if (value instanceof Collection<?> values) {
            return values.stream().filter(Objects::nonNull)
                    .map(String::valueOf).anyMatch(id::equals);
        }
        return value != null && id.equals(String.valueOf(value));
    }

    private static boolean isReservedAttribute(String key) {
        return key.startsWith(CORE_ATTRIBUTE_PREFIX) || key.startsWith(REACT_ATTRIBUTE_PREFIX);
    }
}
