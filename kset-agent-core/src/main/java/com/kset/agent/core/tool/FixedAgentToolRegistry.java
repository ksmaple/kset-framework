package com.kset.agent.core.tool;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Immutable tool registry captured when a kernel is built. */
public final class FixedAgentToolRegistry implements AgentToolRegistry {

    private final Map<String, AgentTool> tools;
    private final List<AgentToolDescriptor> descriptors;

    private FixedAgentToolRegistry(Map<String, AgentTool> tools) {
        this.tools = Map.copyOf(tools);
        this.descriptors = tools.values().stream()
                .map(AgentTool::descriptor)
                .sorted(Comparator.comparing(AgentToolDescriptor::toolName))
                .toList();
    }

    public static FixedAgentToolRegistry copyOf(AgentToolRegistry source) {
        if (source == null) {
            throw new IllegalArgumentException("source tool registry must not be null");
        }
        Map<String, AgentTool> values = new LinkedHashMap<>();
        for (AgentToolDescriptor descriptor : source.list()) {
            if (descriptor == null) {
                throw new IllegalArgumentException("listed tool descriptor must not be null");
            }
            AgentTool tool = source.find(descriptor.toolName())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "tool registry listed a missing tool: " + descriptor.toolName()));
            AgentToolDescriptor fixedDescriptor = tool.descriptor();
            if (fixedDescriptor == null) {
                throw new IllegalArgumentException("tool descriptor must not be null");
            }
            String toolName = normalize(fixedDescriptor.toolName());
            if (toolName.isEmpty()
                    || values.putIfAbsent(
                    toolName, new FixedTool(fixedDescriptor, tool)) != null) {
                throw new IllegalArgumentException("duplicate or blank tool name: " + toolName);
            }
        }
        return new FixedAgentToolRegistry(values);
    }

    @Override
    public Optional<AgentTool> find(String toolName) {
        return Optional.ofNullable(tools.get(normalize(toolName)));
    }

    @Override
    public List<AgentToolDescriptor> list() {
        return descriptors;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private record FixedTool(AgentToolDescriptor descriptor, AgentTool delegate) implements AgentTool {

        @Override
        public ToolExecutionResult execute(
                Map<String, Object> arguments, AgentToolContext context) {
            return delegate.execute(arguments, context);
        }
    }
}
