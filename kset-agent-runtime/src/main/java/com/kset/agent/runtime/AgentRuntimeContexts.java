package com.kset.agent.runtime;

import com.kset.agent.core.api.AgentRequest;
import com.kset.agent.core.execution.AgentExecutionContext;
import com.kset.agent.core.tool.AgentToolContext;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Access to invocation-scoped business facts from model and tool adapters. */
public final class AgentRuntimeContexts {

    private static final String REQUEST_ATTRIBUTE = "kset.runtime.businessContext";

    private AgentRuntimeContexts() {
    }

    public static AgentBusinessContext require(AgentToolContext context) {
        Objects.requireNonNull(context, "context");
        return require(context.executionContext());
    }

    public static AgentBusinessContext require(AgentRequest request) {
        Objects.requireNonNull(request, "request");
        return requireValue(request.attributes().get(REQUEST_ATTRIBUTE));
    }

    public static AgentBusinessContext require(AgentExecutionContext context) {
        Objects.requireNonNull(context, "context");
        return requireValue(context.attributes().get(REQUEST_ATTRIBUTE));
    }

    private static AgentBusinessContext requireValue(Object value) {
        if (value instanceof AgentBusinessContext businessContext) {
            return businessContext;
        }
        throw new IllegalStateException("trusted Agent business context is missing");
    }

    static AgentRequest attach(AgentRequest request, AgentBusinessContext businessContext) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(businessContext, "businessContext");
        if (request.attributes().containsKey(REQUEST_ATTRIBUTE)) {
            throw new IllegalArgumentException("runtime business context is supplied separately");
        }
        Map<String, Object> attributes = new LinkedHashMap<>(request.attributes());
        attributes.put(REQUEST_ATTRIBUTE, businessContext);
        return new AgentRequest(request.agentRunId(), request.task(), request.protocolId(),
                request.options(), attributes, request.cancellation());
    }
}
