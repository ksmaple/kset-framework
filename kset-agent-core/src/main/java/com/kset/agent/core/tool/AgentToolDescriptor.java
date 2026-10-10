package com.kset.agent.core.tool;

import com.kset.agent.core.value.AgentValueSnapshot;

import java.util.Map;

/** Provider-neutral tool description exposed to reasoning strategies. */
public record AgentToolDescriptor(
        String toolName,
        String description,
        Map<String, Object> inputSchema,
        boolean readOnly,
        boolean requiresConfirmation,
        Map<String, Object> metadata) {

    public AgentToolDescriptor {
        if (toolName == null || toolName.isBlank()) {
            throw new IllegalArgumentException("toolName must not be blank");
        }
        toolName = toolName.trim();
        description = description == null ? "" : description;
        inputSchema = AgentValueSnapshot.map(inputSchema);
        metadata = AgentValueSnapshot.map(metadata);
    }
}
