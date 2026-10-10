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
 * Input to a new or resumed agent run. A new request may omit runId when the kernel has a run ID
 * generator; resume requests must provide the snapshot runId. Structured attribute containers
 * are recursively snapshotted; opaque values must be immutable or scoped to this request.
 */
public record AgentRequest(
        String runId,
        String task,
        AgentProtocolId protocolId,
        AgentLoopOptions options,
        Map<String, Object> attributes,
        AgentCancellation cancellation) {

    public static final String CONFIRMED_ACTION_IDS = "agent.confirmedActionIds";
    private static final String CORE_ATTRIBUTE_PREFIX = "agent.";
    private static final String REACT_ATTRIBUTE_PREFIX = "react.";

    public AgentRequest {
        runId = runId == null || runId.isBlank() ? null : runId.trim();
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
            if (isReservedAttribute(key) && !CONFIRMED_ACTION_IDS.equals(key)) {
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
            String runId, String task, AgentProtocolId protocolId) {
        return new AgentRequest(runId, task, protocolId, AgentLoopOptions.defaults(), Map.of(),
                AgentCancellation.none());
    }

    public boolean hasRunId() {
        return runId != null;
    }

    public AgentRequest withRunId(String runId) {
        if (runId == null || runId.isBlank()) {
            throw new AgentCoreException(AgentErrorCode.INVALID_REQUEST,
                    "runId must not be blank");
        }
        return new AgentRequest(runId, task, protocolId, options, attributes, cancellation);
    }

    public boolean isActionConfirmed(String actionId) {
        if (actionId == null || actionId.isBlank()) {
            return false;
        }
        Object value = attributes.get(CONFIRMED_ACTION_IDS);
        if (value instanceof Collection<?> values) {
            return values.stream().filter(Objects::nonNull)
                    .map(String::valueOf).anyMatch(actionId::equals);
        }
        return value != null && actionId.equals(String.valueOf(value));
    }

    private static boolean isReservedAttribute(String key) {
        return key.startsWith(CORE_ATTRIBUTE_PREFIX) || key.startsWith(REACT_ATTRIBUTE_PREFIX);
    }
}
