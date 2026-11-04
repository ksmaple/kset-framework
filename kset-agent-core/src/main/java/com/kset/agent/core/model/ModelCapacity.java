package com.kset.agent.core.model;

/** Published token limits for one exact provider model ID. */
public record ModelCapacity(
        String provider,
        String modelName,
        int contextTokens,
        int maxOutputTokens) {

    public ModelCapacity {
        if (provider == null || provider.isBlank() || modelName == null || modelName.isBlank()
                || contextTokens < 1 || maxOutputTokens < 1
                || maxOutputTokens > contextTokens) {
            throw new IllegalArgumentException("model capacity is invalid");
        }
    }
}
