package com.kset.agent.harness;

import io.agentscope.harness.agent.HarnessAgent;

import java.util.List;
import java.util.Objects;

/** Creates a fresh native Harness builder for each invocation. */
public final class AgentHarnessFactory {
    private final List<AgentHarnessCustomizer> customizers;

    public AgentHarnessFactory(List<AgentHarnessCustomizer> customizers) {
        this.customizers = List.copyOf(Objects.requireNonNull(customizers, "customizers"));
    }

    public HarnessAgent.Builder builder(AgentHarnessSpec spec) {
        Objects.requireNonNull(spec, "spec");
        var builder = HarnessAgent.builder().name(spec.name()).agentId(spec.agentId())
                .model(spec.model()).sysPrompt(spec.systemPrompt()).toolkit(spec.toolkit())
                .stateStore(spec.stateStore()).workspace(spec.workspace()).maxIters(spec.maxIters())
                .maxRetries(0).enablePendingToolRecovery(false)
                .disableFilesystemTools().disableShellTool().disableWebTools()
                .disableMemoryTools().disableMemoryHooks().disableSubagents()
                .disableDynamicSubagents().disableDynamicSkills().disableDefaultWorkspaceSkills()
                .disableToolsConfig().disableAtPathExpansion().disableWorkspaceContext()
                .disableTranscript().disableSessionPersistence().disableToolResultEviction()
                .enableAgentTracingLog(false)
                .middleware(new AgentHarnessTimingMiddleware());
        for (AgentHarnessCustomizer customizer : customizers) {
            customizer.customize(builder);
        }
        return builder;
    }
}
