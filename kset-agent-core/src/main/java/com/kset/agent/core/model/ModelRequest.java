package com.kset.agent.core.model;

import com.kset.agent.core.value.AgentValueSnapshot;

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
        attributes = AgentValueSnapshot.map(attributes);
    }

    public ModelRequest withSystemPrompt(String systemPrompt) {
        return new ModelRequest(systemPrompt, userPrompt, maxOutputTokens, attributes);
    }
}
