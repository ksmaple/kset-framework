package com.kset.agent.core.loop;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.kset.agent.core.tool.InMemoryAgentToolRegistry;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

/** Fluent, framework-neutral assembly entry point for the agent kernel. */
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
        this.model = Objects.requireNonNull(model, "model");
    }

    public AgentKernelBuilder tools(AgentToolRegistry value) {
        this.tools = Objects.requireNonNull(value, "tools");
        return this;
    }

    public AgentKernelBuilder toolExecutor(Executor value) {
        this.toolExecutor = Objects.requireNonNull(value, "toolExecutor");
        return this;
    }

    public AgentKernelBuilder strategy(AgentReasoningStrategy value) {
        this.strategy = Objects.requireNonNull(value, "strategy");
        return this;
    }

    public AgentKernelBuilder checkpoints(AgentCheckpointPort value) {
        this.checkpoints = Objects.requireNonNull(value, "checkpoints");
        return this;
    }

    public AgentKernelBuilder clock(Clock value) {
        this.clock = Objects.requireNonNull(value, "clock");
        return this;
    }

    public AgentKernelBuilder objectMapper(ObjectMapper value) {
        this.objectMapper = Objects.requireNonNull(value, "objectMapper");
        return this;
    }

    public AgentKernelBuilder protocol(AgentProtocolCodec value) {
        this.protocols.add(Objects.requireNonNull(value, "protocol"));
        return this;
    }

    public AgentKernelBuilder actionHandler(AgentActionHandler value) {
        this.actionHandlers.add(Objects.requireNonNull(value, "actionHandler"));
        return this;
    }

    public AgentKernelBuilder stopPolicy(AgentStopPolicy value) {
        this.stopPolicies.add(Objects.requireNonNull(value, "stopPolicy"));
        return this;
    }

    public AgentKernelBuilder listener(AgentLifecycleListener value) {
        this.listeners.add(Objects.requireNonNull(value, "listener"));
        return this;
    }

    public AgentLoopKernel build() {
        List<AgentProtocolCodec> protocolValues = new ArrayList<>();
        protocolValues.add(new AgentJsonV1Codec(objectMapper));
        protocolValues.addAll(protocols);

        List<AgentActionHandler> handlerValues = new ArrayList<>(
                StandardActionHandlers.create(tools, toolExecutor));
        handlerValues.addAll(actionHandlers);

        AgentReasoningStrategy strategyValue = strategy == null
                ? new ReactReasoningStrategy(tools) : strategy;
        return new AgentLoopKernel(model, strategyValue,
                new AgentProtocolRegistry(protocolValues),
                new AgentActionRegistry(handlerValues),
                new AgentStopController(stopPolicies), checkpoints, listeners, clock);
    }
}
