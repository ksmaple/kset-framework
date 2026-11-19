package com.kset.agent.harness;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.tool.AgentTool;

import java.util.List;

/** Supplies tools visible to one invocation; visibility does not grant execution permission. */
@FunctionalInterface
public interface AgentHarnessToolSource {
    List<? extends AgentTool> tools(RuntimeContext context);
}
