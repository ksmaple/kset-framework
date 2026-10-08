package com.kset.agent.core.tool;

import java.util.LinkedHashMap;
import java.util.Map;

/** Provider-neutral tool description exposed to reasoning strategies. */
public record AgentToolDescriptor(
        String name,
        String description,
        Map<String, Object> inputSchema,
        boolean readOnly,
        boolean requiresConfirmation,
        Map<String, Object> metadata) {

    public AgentToolDescriptor {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("tool name must not be blank");
        }
        description = description == null ? "" : description;
        inputSchema = inputSchema == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(inputSchema));
        metadata = metadata == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(metadata));
    }
}
