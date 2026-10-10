package com.kset.agent.core.model;

import com.kset.agent.core.value.AgentValueSnapshot;

import java.util.Map;

/** Provider-native tool call normalized to the core callId/toolName vocabulary. */
public record ModelToolCall(
        String callId,
        String toolName,
        Map<String, Object> arguments) {

    public ModelToolCall {
        callId = normalize(callId);
        toolName = normalize(toolName);
        arguments = AgentValueSnapshot.map(arguments);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
