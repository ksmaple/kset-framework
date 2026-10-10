package com.kset.agent.core.model;

import com.kset.agent.core.value.AgentValueSnapshot;

import java.util.List;
import java.util.Map;

/** Provider-neutral model response, including optional native tool calls. */
public record ModelResponse(
        String text,
        List<ModelToolCall> toolCalls,
        String finishReason,
        ModelCallMetrics metrics,
        Map<String, Object> metadata) {

    public ModelResponse {
        text = text == null ? "" : text;
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        metrics = metrics == null ? ModelCallMetrics.empty() : metrics;
        metadata = AgentValueSnapshot.map(metadata);
    }

    /** Compatibility constructor for callers that do not expose standard call metrics yet. */
    public ModelResponse(String text, List<ModelToolCall> toolCalls,
                         String finishReason, Map<String, Object> metadata) {
        this(text, toolCalls, finishReason, ModelCallMetrics.empty(), metadata);
    }

    public static ModelResponse text(String text) {
        return new ModelResponse(text, List.of(), null, ModelCallMetrics.empty(), Map.of());
    }
}
