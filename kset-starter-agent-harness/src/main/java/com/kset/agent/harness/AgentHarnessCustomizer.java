package com.kset.agent.harness;

import io.agentscope.harness.agent.HarnessAgent;

/** Startup-scoped extension for native Harness options. Implementations must be thread safe. */
@FunctionalInterface
public interface AgentHarnessCustomizer {
    void customize(HarnessAgent.Builder builder);
}
