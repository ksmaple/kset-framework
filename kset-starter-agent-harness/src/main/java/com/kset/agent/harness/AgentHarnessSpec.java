package com.kset.agent.harness;

import io.agentscope.core.model.Model;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.tool.Toolkit;

import java.nio.file.Path;
import java.util.Objects;

/** Values owned by one Agent run; callers provide their own state and authorization boundary. */
public record AgentHarnessSpec(String name, String agentId, Model model, String systemPrompt,
                               Toolkit toolkit, AgentStateStore stateStore, Path workspace, int maxIters) {
    public AgentHarnessSpec {
        if (name == null || name.isBlank() || agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("name and agentId must be set");
        }
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(systemPrompt, "systemPrompt");
        Objects.requireNonNull(toolkit, "toolkit");
        Objects.requireNonNull(stateStore, "stateStore");
        Objects.requireNonNull(workspace, "workspace");
        if (maxIters <= 0) {
            throw new IllegalArgumentException("maxIters must be positive");
        }
    }
}
