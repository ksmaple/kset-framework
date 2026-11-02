package com.kset.agent.runtime.mcp;

import com.kset.agent.core.tool.AgentTool;
import com.kset.agent.core.tool.AgentToolContext;
import com.kset.agent.core.tool.AgentToolDescriptor;
import com.kset.agent.core.tool.ToolExecutionResult;
import com.kset.agent.runtime.AgentBusinessContext;
import com.kset.agent.runtime.AgentRuntimeContexts;
import com.kset.agent.runtime.AgentToolSource;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Adapts an MCP client's current tool catalog to Core tools without owning MCP transport. */
public final class McpAgentToolSource implements AgentToolSource {

    private final McpAgentClient client;

    public McpAgentToolSource(McpAgentClient client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    @Override
    public List<AgentTool> tools(AgentBusinessContext context) {
        Objects.requireNonNull(context, "context");
        List<AgentToolDescriptor> descriptors = Objects.requireNonNull(
                client.listTools(context), "MCP tool catalog");
        return descriptors.stream().map(descriptor -> (AgentTool) new McpTool(
                Objects.requireNonNull(descriptor, "MCP tool descriptor"), client)).toList();
    }

    private record McpTool(AgentToolDescriptor descriptor, McpAgentClient client)
            implements AgentTool {

        @Override
        public ToolExecutionResult execute(Map<String, Object> arguments, AgentToolContext context) {
            AgentBusinessContext businessContext = AgentRuntimeContexts.require(context);
            return client.call(descriptor.toolName(), arguments, context, businessContext);
        }
    }
}
