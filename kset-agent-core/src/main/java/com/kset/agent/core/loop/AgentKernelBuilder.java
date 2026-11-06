package com.kset.agent.core.loop;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kset.agent.core.AgentCoreException;
import com.kset.agent.core.AgentErrorCode;
import com.kset.agent.core.action.AgentActionHandler;
import com.kset.agent.core.action.AgentActionRegistry;
import com.kset.agent.core.action.StandardActionHandlers;
import com.kset.agent.core.action.ToolFlowActionHandler;
import com.kset.agent.core.checkpoint.AgentCheckpointPort;
import com.kset.agent.core.event.AgentLifecycleListener;
import com.kset.agent.core.id.AgentRunIdGenerator;
import com.kset.agent.core.model.AgentModel;
import com.kset.agent.core.model.AgentModelRetryOptions;
import com.kset.agent.core.model.ModelCapacity;
import com.kset.agent.core.model.ModelCapacityCatalog;
import com.kset.agent.core.protocol.AgentProtocolCodec;
import com.kset.agent.core.protocol.AgentProtocolRegistry;
import com.kset.agent.core.protocol.json.AgentJsonV1Codec;
import com.kset.agent.core.protocol.json.AgentJsonFlowV1Codec;
import com.kset.agent.core.stop.AgentStopController;
import com.kset.agent.core.stop.AgentStopPolicy;
import com.kset.agent.core.strategy.AgentReasoningStrategy;
import com.kset.agent.core.strategy.ReactReasoningStrategy;
import com.kset.agent.core.tool.AgentToolRegistry;
import com.kset.agent.core.tool.FixedAgentToolRegistry;
import com.kset.agent.core.tool.InMemoryAgentToolRegistry;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Fluent, framework-neutral assembly entry point. Builders are for single-threaded startup use. */
public final class AgentKernelBuilder {

    private static final Executor DEFAULT_TOOL_EXECUTOR = createDefaultToolExecutor();

    private final AgentModel model;
    private AgentModelRetryOptions modelRetryOptions = AgentModelRetryOptions.defaults();
    private AgentToolRegistry toolRegistry = new InMemoryAgentToolRegistry();
    private Executor toolExecutor = DEFAULT_TOOL_EXECUTOR;
    private AgentReasoningStrategy reasoningStrategy;
    private AgentCheckpointPort checkpointPort = AgentCheckpointPort.noop();
    private Clock clock = Clock.systemUTC();
    private AgentRunIdGenerator agentRunIdGenerator;
    private ObjectMapper objectMapper = new ObjectMapper();
    private boolean readOnlyToolFlowEnabled;
    private final List<AgentProtocolCodec> protocolCodecs = new ArrayList<>();
    private final List<AgentActionHandler> actionHandlers = new ArrayList<>();
    private final List<AgentStopPolicy> stopPolicies = new ArrayList<>();
    private final List<AgentLifecycleListener> listeners = new ArrayList<>();
    private final List<ModelCapacity> modelCapacities = new ArrayList<>();

    AgentKernelBuilder(AgentModel model) {
        this.model = requireConfiguration(model, "model");
    }

    public AgentKernelBuilder tools(AgentToolRegistry toolRegistry) {
        this.toolRegistry = requireConfiguration(toolRegistry, "toolRegistry");
        return this;
    }

    public AgentKernelBuilder toolExecutor(Executor toolExecutor) {
        this.toolExecutor = requireConfiguration(toolExecutor, "toolExecutor");
        return this;
    }

    public AgentKernelBuilder strategy(AgentReasoningStrategy reasoningStrategy) {
        this.reasoningStrategy = requireConfiguration(reasoningStrategy, "reasoningStrategy");
        return this;
    }

    public AgentKernelBuilder checkpoints(AgentCheckpointPort checkpointPort) {
        this.checkpointPort = requireConfiguration(checkpointPort, "checkpointPort");
        return this;
    }

    public AgentKernelBuilder clock(Clock clock) {
        this.clock = requireConfiguration(clock, "clock");
        return this;
    }

    /** Configures bounded retries for explicitly retryable model-call failures. */
    public AgentKernelBuilder modelRetry(AgentModelRetryOptions modelRetryOptions) {
        this.modelRetryOptions = requireConfiguration(modelRetryOptions, "modelRetryOptions");
        return this;
    }

    /** Registers a model capacity for this Kernel; custom IDs override built-in entries. */
    public AgentKernelBuilder modelCapacity(ModelCapacity capacity) {
        modelCapacities.add(requireConfiguration(capacity, "modelCapacity"));
        return this;
    }

    /** Configures the generator used only when a new run request has no explicit agentRunId. */
    public AgentKernelBuilder agentRunIdGenerator(AgentRunIdGenerator agentRunIdGenerator) {
        this.agentRunIdGenerator = requireConfiguration(agentRunIdGenerator, "agentRunIdGenerator");
        return this;
    }

    public AgentKernelBuilder objectMapper(ObjectMapper objectMapper) {
        this.objectMapper = requireConfiguration(objectMapper, "objectMapper");
        return this;
    }

    /** Enables the opt-in read-only tool_flow protocol and dependency handler. */
    public AgentKernelBuilder readOnlyToolFlow() {
        readOnlyToolFlowEnabled = true;
        return this;
    }

    public AgentKernelBuilder protocol(AgentProtocolCodec protocolCodec) {
        this.protocolCodecs.add(requireConfiguration(protocolCodec, "protocolCodec"));
        return this;
    }

    public AgentKernelBuilder actionHandler(AgentActionHandler actionHandler) {
        this.actionHandlers.add(requireConfiguration(actionHandler, "actionHandler"));
        return this;
    }

    public AgentKernelBuilder stopPolicy(AgentStopPolicy stopPolicy) {
        this.stopPolicies.add(requireConfiguration(stopPolicy, "stopPolicy"));
        return this;
    }

    public AgentKernelBuilder listener(AgentLifecycleListener lifecycleListener) {
        this.listeners.add(requireConfiguration(lifecycleListener, "lifecycleListener"));
        return this;
    }

    public AgentLoopKernel build() {
        try {
            AgentToolRegistry fixedToolRegistry = FixedAgentToolRegistry.copyOf(toolRegistry);
            List<AgentProtocolCodec> registeredProtocolCodecs = new ArrayList<>();
            registeredProtocolCodecs.add(new AgentJsonV1Codec(objectMapper));
            if (readOnlyToolFlowEnabled) {
                registeredProtocolCodecs.add(new AgentJsonFlowV1Codec(objectMapper));
            }
            registeredProtocolCodecs.addAll(protocolCodecs);

            List<AgentActionHandler> registeredActionHandlers = new ArrayList<>(
                    StandardActionHandlers.create(fixedToolRegistry, toolExecutor));
            if (readOnlyToolFlowEnabled) {
                registeredActionHandlers.add(new ToolFlowActionHandler(
                        fixedToolRegistry, toolExecutor, objectMapper));
            }
            registeredActionHandlers.addAll(actionHandlers);

            AgentReasoningStrategy selectedReasoningStrategy = reasoningStrategy == null
                    ? new ReactReasoningStrategy(fixedToolRegistry) : reasoningStrategy;
            String strategyId = requireStrategyId(selectedReasoningStrategy);
            ModelCapacityCatalog capacities = ModelCapacityCatalog.withOverrides(modelCapacities);
            return new AgentLoopKernel(model, modelRetryOptions, capacities,
                    selectedReasoningStrategy, strategyId,
                    new AgentProtocolRegistry(registeredProtocolCodecs),
                    new AgentActionRegistry(registeredActionHandlers),
                    new AgentStopController(stopPolicies), checkpointPort, listeners, clock,
                    agentRunIdGenerator);
        } catch (AgentCoreException error) {
            throw error;
        } catch (RuntimeException error) {
            throw new AgentCoreException(AgentErrorCode.INVALID_CONFIGURATION,
                    "agent kernel configuration is invalid: " + errorMessage(error), error);
        }
    }

    private static Executor createDefaultToolExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(8, 8, 30L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(64),
                Thread.ofPlatform().daemon(true).name("kset-agent-tool-", 0).factory(),
                new ThreadPoolExecutor.CallerRunsPolicy());
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    private static <T> T requireConfiguration(T configuration, String configurationName) {
        if (configuration == null) {
            throw new AgentCoreException(AgentErrorCode.INVALID_CONFIGURATION,
                    configurationName + " must not be null");
        }
        return configuration;
    }

    private static String requireStrategyId(AgentReasoningStrategy reasoningStrategy) {
        String strategyId = reasoningStrategy.strategyId();
        if (strategyId == null || strategyId.isBlank()) {
            throw new AgentCoreException(AgentErrorCode.INVALID_CONFIGURATION,
                    "strategy id must not be blank");
        }
        String normalized = strategyId.trim();
        if ("agent".equals(normalized) || normalized.startsWith("agent.")) {
            throw new AgentCoreException(AgentErrorCode.INVALID_CONFIGURATION,
                    "strategy id must not use the agent namespace");
        }
        return normalized;
    }

    private static String errorMessage(Throwable error) {
        return error.getMessage() == null || error.getMessage().isBlank()
                ? error.getClass().getSimpleName() : error.getMessage();
    }
}
