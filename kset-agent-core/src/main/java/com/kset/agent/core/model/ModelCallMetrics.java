package com.kset.agent.core.model;

import java.util.LinkedHashMap;
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
        attributes = attributes == null
                ? Map.of() : Map.copyOf(new LinkedHashMap<>(attributes));
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

    private static void requireNonNegative(Long value, String name) {
        if (value != null && value < 0L) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }
}
