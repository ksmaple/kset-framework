package com.kset.agent.core.loop;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kset.agent.core.AgentCoreException;
import com.kset.agent.core.AgentErrorCode;
import com.kset.agent.core.action.AgentActionHandler;
import com.kset.agent.core.action.AgentActionRegistry;
import com.kset.agent.core.action.StandardActionHandlers;
import com.kset.agent.core.checkpoint.AgentCheckpointPort;
import com.kset.agent.core.event.AgentLifecycleListener;
import com.kset.agent.core.id.AgentIdGenerator;
import com.kset.agent.core.model.AgentModel;
import com.kset.agent.core.protocol.AgentProtocolCodec;
import com.kset.agent.core.protocol.AgentProtocolRegistry;
import com.kset.agent.core.protocol.json.AgentJsonV1Codec;
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
import java.util.concurrent.Executor;

/** Fluent, framework-neutral assembly entry point. Builders are for single-threaded startup use. */
public final class AgentKernelBuilder {

    private final AgentModel model;
    private AgentToolRegistry toolRegistry = new InMemoryAgentToolRegistry();
    private Executor toolExecutor = Runnable::run;
    private AgentReasoningStrategy reasoningStrategy;
    private AgentCheckpointPort checkpointPort = AgentCheckpointPort.noop();
    private Clock clock = Clock.systemUTC();
    private AgentIdGenerator runIdGenerator;
    private ObjectMapper objectMapper = new ObjectMapper();
    private final List<AgentProtocolCodec> protocolCodecs = new ArrayList<>();
    private final List<AgentActionHandler> actionHandlers = new ArrayList<>();
    private final List<AgentStopPolicy> stopPolicies = new ArrayList<>();
    private final List<AgentLifecycleListener> listeners = new ArrayList<>();

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

    /** Configures the generator used only when a new run request has no explicit runId. */
    public AgentKernelBuilder runIdGenerator(AgentIdGenerator runIdGenerator) {
        this.runIdGenerator = requireConfiguration(runIdGenerator, "runIdGenerator");
        return this;
    }

    public AgentKernelBuilder objectMapper(ObjectMapper objectMapper) {
        this.objectMapper = requireConfiguration(objectMapper, "objectMapper");
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
            registeredProtocolCodecs.addAll(protocolCodecs);

            List<AgentActionHandler> registeredActionHandlers = new ArrayList<>(
                    StandardActionHandlers.create(fixedToolRegistry, toolExecutor));
            registeredActionHandlers.addAll(actionHandlers);

            AgentReasoningStrategy selectedReasoningStrategy = reasoningStrategy == null
                    ? new ReactReasoningStrategy(fixedToolRegistry) : reasoningStrategy;
            String strategyId = requireStrategyId(selectedReasoningStrategy);
            return new AgentLoopKernel(model, selectedReasoningStrategy, strategyId,
                    new AgentProtocolRegistry(registeredProtocolCodecs),
                    new AgentActionRegistry(registeredActionHandlers),
                    new AgentStopController(stopPolicies), checkpointPort, listeners, clock,
                    runIdGenerator);
        } catch (AgentCoreException error) {
            throw error;
        } catch (RuntimeException error) {
            throw new AgentCoreException(AgentErrorCode.INVALID_CONFIGURATION,
                    "agent kernel configuration is invalid: " + errorMessage(error), error);
        }
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
