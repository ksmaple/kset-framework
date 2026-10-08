package com.kset.agent.core.model;

import java.util.LinkedHashMap;
import java.util.Map;

/** Provider-neutral model request. */
public record ModelRequest(
        String systemPrompt,
        String userPrompt,
        int maxOutputTokens,
        Map<String, Object> attributes) {

    public ModelRequest {
        systemPrompt = systemPrompt == null ? "" : systemPrompt;
        userPrompt = userPrompt == null ? "" : userPrompt;
        if (maxOutputTokens < 0) {
            throw new IllegalArgumentException("maxOutputTokens must not be negative");
        }
        attributes = attributes == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(attributes));
    }

    public ModelRequest withSystemPrompt(String prompt) {
        return new ModelRequest(prompt, userPrompt, maxOutputTokens, attributes);
    }
}
