package com.kset.agent.core.tool;

import java.util.Map;

/** Executable tool boundary. */
public interface AgentTool {

    AgentToolDescriptor descriptor();

    ToolExecutionResult execute(Map<String, Object> arguments);
}
