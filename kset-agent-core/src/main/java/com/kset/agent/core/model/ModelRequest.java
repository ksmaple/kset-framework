package com.kset.agent.core.model;

import com.kset.agent.core.value.AgentValueSnapshot;

import java.util.Map;

/**
 * Provider-neutral model request. The strategy supplies {@code attributes} as context for the
 * model adapter to interpret; the adapter must select provider fields explicitly instead of
 * forwarding the entire map. Prompt character limits do not replace provider token-window checks.
 */
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
