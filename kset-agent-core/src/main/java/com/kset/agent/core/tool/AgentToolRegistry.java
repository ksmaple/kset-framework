package com.kset.agent.core.tool;

import java.util.List;
import java.util.Optional;

public interface AgentToolRegistry {

    Optional<AgentTool> find(String name);

    List<AgentToolDescriptor> list();
}
