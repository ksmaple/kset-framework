package com.kset.agent.runtime;

import com.kset.agent.core.tool.AgentTool;

import java.util.List;

/** Builds the visible tool catalog for one invocation. Actual authorization stays with each tool. */
@FunctionalInterface
public interface AgentToolSource {

    List<AgentTool> tools(AgentBusinessContext context);
}
