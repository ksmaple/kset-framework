package com.kset.agent.core.model;

import com.kset.agent.core.value.AgentValueSnapshot;

import java.util.Map;

/** Provider-neutral model identity and usage values intended for external telemetry. */
public record ModelCallMetrics(
        String provider,
        String model,
        String requestId,
        Long inputTokens,
        Long outputTokens,
        Long totalTokens,
        Map<String, Object> attributes) {

    public ModelCallMetrics {
        provider = normalize(provider);
        model = normalize(model);
        requestId = normalize(requestId);
        requireNonNegative(inputTokens, "inputTokens");
        requireNonNegative(outputTokens, "outputTokens");
        requireNonNegative(totalTokens, "totalTokens");
        attributes = AgentValueSnapshot.map(attributes);
    }

    public static ModelCallMetrics empty() {
        return new ModelCallMetrics(null, null, null, null, null, null, Map.of());
    }

    public boolean isEmpty() {
        return provider == null && model == null && requestId == null
                && inputTokens == null && outputTokens == null && totalTokens == null
                && attributes.isEmpty();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static void requireNonNegative(Long tokenCount, String fieldName) {
        if (tokenCount != null && tokenCount < 0L) {
            throw new IllegalArgumentException(fieldName + " must not be negative");
        }
    }
}
