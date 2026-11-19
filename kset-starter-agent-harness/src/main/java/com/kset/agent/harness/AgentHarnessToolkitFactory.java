package com.kset.agent.harness;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.ToolkitConfig;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Resolves a fixed native tool catalog for one invocation. */
public final class AgentHarnessToolkitFactory {
    public Toolkit create(RuntimeContext context, ToolkitConfig config,
                          List<AgentHarnessToolSource> sources) {
        Objects.requireNonNull(context, "context");
        if (context.getUserId() == null || context.getUserId().isBlank()
                || context.getSessionId() == null || context.getSessionId().isBlank()) {
            throw new IllegalArgumentException("userId and sessionId must be set");
        }
        Objects.requireNonNull(config, "config");
        List<AgentHarnessToolSource> selected = List.copyOf(Objects.requireNonNull(sources, "sources"));
        List<AgentTool> tools = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (AgentHarnessToolSource source : selected) {
            List<? extends AgentTool> candidates = Objects.requireNonNull(
                    source.tools(context), "tool source result");
            for (AgentTool tool : candidates) {
                Objects.requireNonNull(tool, "tool");
                String name = tool.getName();
                if (name == null || name.isBlank() || !names.add(name)) {
                    throw new IllegalArgumentException("tool name is blank or duplicated: " + name);
                }
                tools.add(tool);
            }
        }
        Toolkit toolkit = new Toolkit(config);
        tools.forEach(toolkit::registerAgentTool);
        return toolkit;
    }
}
