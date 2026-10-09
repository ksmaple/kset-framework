package com.kset.agent.core.loop;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kset.agent.core.AgentCoreException;
import com.kset.agent.core.AgentErrorCode;
import com.kset.agent.core.action.AgentActionHandler;
import com.kset.agent.core.action.AgentActionRegistry;
import com.kset.agent.core.action.StandardActionHandlers;
import com.kset.agent.core.checkpoint.AgentCheckpointPort;
import com.kset.agent.core.event.AgentLifecycleListener;
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
    private AgentToolRegistry tools = new InMemoryAgentToolRegistry();
    private Executor toolExecutor = Runnable::run;
    private AgentReasoningStrategy strategy;
    private AgentCheckpointPort checkpoints = AgentCheckpointPort.noop();
    private Clock clock = Clock.systemUTC();
    private ObjectMapper objectMapper = new ObjectMapper();
    private final List<AgentProtocolCodec> protocols = new ArrayList<>();
    private final List<AgentActionHandler> actionHandlers = new ArrayList<>();
    private final List<AgentStopPolicy> stopPolicies = new ArrayList<>();
    private final List<AgentLifecycleListener> listeners = new ArrayList<>();

    AgentKernelBuilder(AgentModel model) {
        this.model = requireConfiguration(model, "model");
    }

    public AgentKernelBuilder tools(AgentToolRegistry value) {
        this.tools = requireConfiguration(value, "tools");
        return this;
    }

    public AgentKernelBuilder toolExecutor(Executor value) {
        this.toolExecutor = requireConfiguration(value, "toolExecutor");
        return this;
    }

    public AgentKernelBuilder strategy(AgentReasoningStrategy value) {
        this.strategy = requireConfiguration(value, "strategy");
        return this;
    }

    public AgentKernelBuilder checkpoints(AgentCheckpointPort value) {
        this.checkpoints = requireConfiguration(value, "checkpoints");
        return this;
    }

    public AgentKernelBuilder clock(Clock value) {
        this.clock = requireConfiguration(value, "clock");
        return this;
    }

    public AgentKernelBuilder objectMapper(ObjectMapper value) {
        this.objectMapper = requireConfiguration(value, "objectMapper");
        return this;
    }

    public AgentKernelBuilder protocol(AgentProtocolCodec value) {
        this.protocols.add(requireConfiguration(value, "protocol"));
        return this;
    }

    public AgentKernelBuilder actionHandler(AgentActionHandler value) {
        this.actionHandlers.add(requireConfiguration(value, "actionHandler"));
        return this;
    }

    public AgentKernelBuilder stopPolicy(AgentStopPolicy value) {
        this.stopPolicies.add(requireConfiguration(value, "stopPolicy"));
        return this;
    }

    public AgentKernelBuilder listener(AgentLifecycleListener value) {
        this.listeners.add(requireConfiguration(value, "listener"));
        return this;
    }

    public AgentLoopKernel build() {
        try {
            AgentToolRegistry fixedTools = FixedAgentToolRegistry.copyOf(tools);
            List<AgentProtocolCodec> protocolValues = new ArrayList<>();
            protocolValues.add(new AgentJsonV1Codec(objectMapper));
            protocolValues.addAll(protocols);

            List<AgentActionHandler> handlerValues = new ArrayList<>(
                    StandardActionHandlers.create(fixedTools, toolExecutor));
            handlerValues.addAll(actionHandlers);

            AgentReasoningStrategy strategyValue = strategy == null
                    ? new ReactReasoningStrategy(fixedTools) : strategy;
            return new AgentLoopKernel(model, strategyValue,
                    new AgentProtocolRegistry(protocolValues),
                    new AgentActionRegistry(handlerValues),
                    new AgentStopController(stopPolicies), checkpoints, listeners, clock);
        } catch (AgentCoreException error) {
            throw error;
        } catch (RuntimeException error) {
            throw new AgentCoreException(AgentErrorCode.INVALID_CONFIGURATION,
                    "agent kernel configuration is invalid: " + errorMessage(error), error);
        }
    }

    private static <T> T requireConfiguration(T value, String name) {
        if (value == null) {
            throw new AgentCoreException(AgentErrorCode.INVALID_CONFIGURATION,
                    name + " must not be null");
        }
        return value;
    }

    private static String errorMessage(Throwable error) {
        return error.getMessage() == null || error.getMessage().isBlank()
                ? error.getClass().getSimpleName() : error.getMessage();
    }
}
