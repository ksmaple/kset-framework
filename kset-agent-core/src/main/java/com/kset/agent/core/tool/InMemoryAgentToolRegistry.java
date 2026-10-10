package com.kset.agent.core.tool;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe mutable registry for startup assembly. Kernels capture an immutable copy at build time.
 */
public final class InMemoryAgentToolRegistry implements AgentToolRegistry {

    private final Map<String, AgentTool> tools = new ConcurrentHashMap<>();

    public InMemoryAgentToolRegistry register(AgentTool tool) {
        if (tool == null || tool.descriptor() == null) {
            throw new IllegalArgumentException("tool and descriptor must not be null");
        }
        String toolName = normalize(tool.descriptor().toolName());
        if (tools.putIfAbsent(toolName, tool) != null) {
            throw new IllegalArgumentException("duplicate tool name: " + toolName);
        }
        return this;
    }

    public void unregister(String toolName) {
        tools.remove(normalize(toolName));
    }

    @Override
    public Optional<AgentTool> find(String toolName) {
        return Optional.ofNullable(tools.get(normalize(toolName)));
    }

    @Override
    public List<AgentToolDescriptor> list() {
        return tools.values().stream()
                .map(AgentTool::descriptor)
                .sorted(java.util.Comparator.comparing(AgentToolDescriptor::toolName))
                .toList();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
