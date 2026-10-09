package com.kset.agent.core.tool;

import java.util.List;
import java.util.Optional;

/** Tool lookup boundary. Implementations shared by a running kernel must be thread-safe. */
public interface AgentToolRegistry {

    Optional<AgentTool> find(String name);

    List<AgentToolDescriptor> list();
}
