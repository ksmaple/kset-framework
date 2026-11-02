package com.kset.agent.runtime.mcp;

import com.kset.agent.core.tool.AgentToolContext;
import com.kset.agent.core.tool.AgentToolDescriptor;
import com.kset.agent.core.tool.ToolExecutionResult;
import com.kset.agent.runtime.AgentBusinessContext;

import java.util.List;
import java.util.Map;

/** Host-owned MCP transport and credential boundary. Calls must verify current access at the server. */
public interface McpAgentClient {

    List<AgentToolDescriptor> listTools(AgentBusinessContext context);

    ToolExecutionResult call(String toolName, Map<String, Object> arguments,
                             AgentToolContext toolContext, AgentBusinessContext businessContext);
}
