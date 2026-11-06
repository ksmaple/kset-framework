package com.kset.agent.runtime;

import com.kset.agent.core.action.AgentActionHandler;
import com.kset.agent.core.api.AgentRequest;
import com.kset.agent.core.api.AgentResult;
import com.kset.agent.core.api.AgentResumeInput;
import com.kset.agent.core.checkpoint.AgentCheckpointPort;
import com.kset.agent.core.event.AgentLifecycleContext;
import com.kset.agent.core.event.AgentLifecycleListener;
import com.kset.agent.core.event.AgentTimingLogListener;
import com.kset.agent.core.id.AgentRunIdGenerator;
import com.kset.agent.core.loop.AgentKernelBuilder;
import com.kset.agent.core.loop.AgentLoopKernel;
import com.kset.agent.core.loop.AgentRunSnapshot;
import com.kset.agent.core.loop.AgentRunState;
import com.kset.agent.core.model.AgentModel;
import com.kset.agent.core.model.AgentModelRetryOptions;
import com.kset.agent.core.model.ModelCallMetrics;
import com.kset.agent.core.model.ModelCapacity;
import com.kset.agent.core.model.ModelCapacityCatalog;
import com.kset.agent.core.model.ModelResponse;
import com.kset.agent.core.protocol.AgentProtocolCodec;
import com.kset.agent.core.stop.AgentStopPolicy;
import com.kset.agent.core.strategy.AgentReasoningStrategy;
import com.kset.agent.core.tool.AgentTool;
import com.kset.agent.core.tool.AgentToolContext;
import com.kset.agent.core.tool.AgentToolDescriptor;
import com.kset.agent.core.tool.AgentToolRegistry;
import com.kset.agent.core.tool.FixedAgentToolRegistry;
import com.kset.agent.core.tool.InMemoryAgentToolRegistry;
import com.kset.agent.core.tool.ToolExecutionResult;
import com.kset.agent.runtime.mcp.McpAgentClient;
import com.kset.agent.runtime.mcp.McpAgentToolSource;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/** Per-invocation assembly around Core; Core remains the only execution loop. */
public final class AgentRuntime {

    private static final AgentTimingLogListener TIMING_LOG_LISTENER = new AgentTimingLogListener();

    private final AgentModel model;
    private final Function<AgentBusinessContext, AgentModel> modelFactory;
    private final List<AgentToolSource> toolSources;
    private final Map<String, AgentSkill> skills;
    private final Function<AgentToolRegistry, AgentReasoningStrategy> strategyFactory;
    private final List<AgentProtocolCodec> protocols;
    private final List<AgentActionHandler> actionHandlers;
    private final List<AgentStopPolicy> stopPolicies;
    private final List<AgentLifecycleListener> listeners;
    private final AgentCheckpointPort checkpointPort;
    private final Executor toolExecutor;
    private final boolean readOnlyToolFlowEnabled;
    private final Clock clock;
    private final AgentRunIdGenerator agentRunIdGenerator;
    private final AgentModelRetryOptions modelRetryOptions;
    private final List<ModelCapacity> modelCapacities;

    private AgentRuntime(Builder builder) {
        model = builder.model;
        modelFactory = builder.modelFactory;
        toolSources = List.copyOf(builder.toolSources);
        skills = Map.copyOf(builder.skills);
        strategyFactory = builder.strategyFactory;
        protocols = List.copyOf(builder.protocols);
        actionHandlers = List.copyOf(builder.actionHandlers);
        stopPolicies = List.copyOf(builder.stopPolicies);
        listeners = List.copyOf(builder.listeners);
        checkpointPort = builder.checkpointPort;
        toolExecutor = builder.toolExecutor;
        readOnlyToolFlowEnabled = builder.readOnlyToolFlowEnabled;
        clock = builder.clock;
        agentRunIdGenerator = builder.agentRunIdGenerator;
        modelRetryOptions = builder.modelRetryOptions;
        modelCapacities = List.copyOf(builder.modelCapacities);
        ModelCapacityCatalog.withOverrides(modelCapacities);
    }

    public static Builder builder(AgentModel model) {
        return new Builder(model);
    }

    public static Builder builder(String modelName, AgentModel model) {
        return new Builder(AgentModel.named(modelName, model));
    }

    public AgentResult run(AgentRequest request, AgentBusinessContext context) {
        return run(request, context, List.of());
    }

    public AgentResult run(AgentRequest request, AgentBusinessContext context,
                           List<String> selectedSkillIds) {
        AgentRequest boundRequest = AgentRuntimeContexts.attach(request, context);
        return kernel(context, selectedSkillIds).run(boundRequest);
    }

    public AgentResult resume(AgentRequest request, AgentRunSnapshot snapshot,
                              AgentBusinessContext context) {
        return resume(request, snapshot, AgentResumeInput.none(), context, List.of());
    }

    public AgentResult resume(AgentRequest request, AgentRunSnapshot snapshot,
                              AgentResumeInput input, AgentBusinessContext context,
                              List<String> selectedSkillIds) {
        AgentRequest boundRequest = AgentRuntimeContexts.attach(request, context);
        return kernel(context, selectedSkillIds).resume(boundRequest, snapshot, input);
    }

    private AgentLoopKernel kernel(AgentBusinessContext context, List<String> selectedSkillIds) {
        InMemoryAgentToolRegistry tools = new InMemoryAgentToolRegistry();
        for (AgentToolSource source : toolSources) {
            registerTools(tools, source, context);
        }
        Set<String> selected = new HashSet<>();
        for (String skillId : List.copyOf(Objects.requireNonNull(selectedSkillIds,
                "selectedSkillIds"))) {
            if (!selected.add(skillId)) {
                throw new IllegalArgumentException("duplicate selected skill: " + skillId);
            }
            AgentSkill skill = skills.get(skillId);
            if (skill == null) {
                throw new IllegalArgumentException("unknown selected skill: " + skillId);
            }
            registerTools(tools, skill.toolSource(), context);
        }
        AgentToolRegistry fixedTools = FixedAgentToolRegistry.copyOf(tools);
        AgentModel selectedModel = modelFactory == null ? model
                : Objects.requireNonNull(modelFactory.apply(context), "model factory result");
        AgentKernelBuilder builder = AgentLoopKernel.builder(selectedModel)
                .tools(fixedTools)
                .checkpoints(checkpointPort)
                .clock(clock)
                .modelRetry(modelRetryOptions);
        if (toolExecutor != null) {
            builder.toolExecutor(toolExecutor);
        }
        if (readOnlyToolFlowEnabled) {
            builder.readOnlyToolFlow();
        }
        modelCapacities.forEach(builder::modelCapacity);
        if (agentRunIdGenerator != null) {
            builder.agentRunIdGenerator(agentRunIdGenerator);
        }
        if (strategyFactory != null) {
            builder.strategy(Objects.requireNonNull(strategyFactory.apply(fixedTools),
                    "reasoning strategy"));
        }
        protocols.forEach(builder::protocol);
        actionHandlers.forEach(builder::actionHandler);
        stopPolicies.forEach(builder::stopPolicy);
        builder.listener(TIMING_LOG_LISTENER);
        listeners.forEach(builder::listener);
        return builder.build();
    }

    private static void registerTools(InMemoryAgentToolRegistry registry,
                                      AgentToolSource source, AgentBusinessContext context) {
        List<AgentTool> candidates = Objects.requireNonNull(source.tools(context),
                "tool source result");
        for (AgentTool tool : candidates) {
            registry.register(Objects.requireNonNull(tool, "tool"));
        }
    }

    /** Startup configuration; build a separate Runtime for mutable, run-specific collaborators. */
    public static final class Builder {

        private final AgentModel model;
        private Function<AgentBusinessContext, AgentModel> modelFactory;
        private final List<AgentToolSource> toolSources = new ArrayList<>();
        private final Map<String, AgentSkill> skills = new LinkedHashMap<>();
        private Function<AgentToolRegistry, AgentReasoningStrategy> strategyFactory;
        private final List<AgentProtocolCodec> protocols = new ArrayList<>();
        private final List<AgentActionHandler> actionHandlers = new ArrayList<>();
        private final List<AgentStopPolicy> stopPolicies = new ArrayList<>();
        private final List<AgentLifecycleListener> listeners = new ArrayList<>();
        private AgentCheckpointPort checkpointPort = AgentCheckpointPort.noop();
        private Executor toolExecutor;
        private boolean readOnlyToolFlowEnabled;
        private Clock clock = Clock.systemUTC();
        private AgentRunIdGenerator agentRunIdGenerator;
        private AgentModelRetryOptions modelRetryOptions = AgentModelRetryOptions.defaults();
        private final List<ModelCapacity> modelCapacities = new ArrayList<>();

        private Builder(AgentModel model) {
            this.model = Objects.requireNonNull(model, "model");
        }

        public Builder modelFactory(Function<AgentBusinessContext, AgentModel> factory) {
            modelFactory = Objects.requireNonNull(factory, "factory");
            return this;
        }

        /** Registers a model capacity for this Runtime; custom IDs override built-in entries. */
        public Builder modelCapacity(ModelCapacity capacity) {
            modelCapacities.add(Objects.requireNonNull(capacity, "capacity"));
            return this;
        }

        public Builder tools(AgentToolSource source) {
            toolSources.add(Objects.requireNonNull(source, "source"));
            return this;
        }

        public Builder tool(AgentTool tool) {
            AgentTool registeredTool = Objects.requireNonNull(tool, "tool");
            return tools(context -> List.of(registeredTool));
        }

        public Builder tool(AgentToolDescriptor descriptor,
                            BiFunction<Map<String, Object>, AgentToolContext,
                                    ToolExecutionResult> handler) {
            AgentToolDescriptor registeredDescriptor = Objects.requireNonNull(descriptor, "descriptor");
            BiFunction<Map<String, Object>, AgentToolContext, ToolExecutionResult> registeredHandler =
                    Objects.requireNonNull(handler, "handler");
            return tool(new AgentTool() {
                @Override
                public AgentToolDescriptor descriptor() {
                    return registeredDescriptor;
                }

                @Override
                public ToolExecutionResult execute(Map<String, Object> arguments,
                                                   AgentToolContext context) {
                    return registeredHandler.apply(arguments, context);
                }
            });
        }

        /** Adapts an existing Core registry when the host already builds one per run. */
        public Builder tools(AgentToolRegistry registry) {
            Objects.requireNonNull(registry, "registry");
            return tools(context -> registry.list().stream()
                    .map(descriptor -> registry.find(descriptor.toolName()).orElseThrow())
                    .toList());
        }

        public Builder mcpTools(McpAgentClient client) {
            return tools(new McpAgentToolSource(client));
        }

        public Builder skill(AgentSkill skill) {
            Objects.requireNonNull(skill, "skill");
            if (skills.putIfAbsent(skill.skillId(), skill) != null) {
                throw new IllegalArgumentException("duplicate skill: " + skill.skillId());
            }
            return this;
        }

        public Builder skill(String skillId, AgentToolSource source) {
            return skill(new AgentSkill(skillId, source));
        }

        public Builder strategy(AgentReasoningStrategy strategy) {
            Objects.requireNonNull(strategy, "strategy");
            strategyFactory = ignored -> strategy;
            return this;
        }

        public Builder strategyFactory(
                Function<AgentToolRegistry, AgentReasoningStrategy> factory) {
            strategyFactory = Objects.requireNonNull(factory, "factory");
            return this;
        }

        public Builder protocol(AgentProtocolCodec codec) {
            protocols.add(Objects.requireNonNull(codec, "codec"));
            return this;
        }

        public Builder actionHandler(AgentActionHandler handler) {
            actionHandlers.add(Objects.requireNonNull(handler, "handler"));
            return this;
        }

        public Builder stopPolicy(AgentStopPolicy policy) {
            stopPolicies.add(Objects.requireNonNull(policy, "policy"));
            return this;
        }

        public Builder listener(AgentLifecycleListener listener) {
            listeners.add(Objects.requireNonNull(listener, "listener"));
            return this;
        }

        public Builder onEvent(Consumer<AgentLifecycleContext> callback) {
            Consumer<AgentLifecycleContext> registeredCallback =
                    Objects.requireNonNull(callback, "callback");
            return listener(new AgentLifecycleListener() {
                @Override
                public void onEvent(AgentLifecycleContext context) {
                    registeredCallback.accept(context);
                }
            });
        }

        public Builder onModelMetrics(
                BiConsumer<AgentLifecycleContext, ModelCallMetrics> callback) {
            BiConsumer<AgentLifecycleContext, ModelCallMetrics> registeredCallback =
                    Objects.requireNonNull(callback, "callback");
            return listener(new AgentLifecycleListener() {
                @Override
                public void afterModel(AgentLifecycleContext context, AgentRequest request,
                                       AgentRunState state,
                                       ModelResponse response) {
                    if (!response.metrics().isEmpty()) {
                        registeredCallback.accept(context, response.metrics());
                    }
                }
            });
        }

        public Builder checkpoints(AgentCheckpointPort port) {
            checkpointPort = Objects.requireNonNull(port, "port");
            return this;
        }

        public Builder toolExecutor(Executor executor) {
            toolExecutor = Objects.requireNonNull(executor, "executor");
            return this;
        }

        /** Enables read-only tool_flow for each invocation's selected tool registry. */
        public Builder readOnlyToolFlow() {
            readOnlyToolFlowEnabled = true;
            return this;
        }

        public Builder clock(Clock value) {
            clock = Objects.requireNonNull(value, "clock");
            return this;
        }

        public Builder agentRunIdGenerator(AgentRunIdGenerator generator) {
            agentRunIdGenerator = Objects.requireNonNull(generator, "generator");
            return this;
        }

        public Builder modelRetry(AgentModelRetryOptions options) {
            modelRetryOptions = Objects.requireNonNull(options, "options");
            return this;
        }

        public AgentRuntime build() {
            return new AgentRuntime(this);
        }
    }
}
