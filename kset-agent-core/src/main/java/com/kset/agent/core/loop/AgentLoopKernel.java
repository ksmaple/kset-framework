package com.kset.agent.core.loop;

import com.kset.agent.core.AgentCoreException;
import com.kset.agent.core.action.AgentAction;
import com.kset.agent.core.action.AgentActionContext;
import com.kset.agent.core.action.AgentActionRegistry;
import com.kset.agent.core.action.AgentActionResult;
import com.kset.agent.core.api.AgentRequest;
import com.kset.agent.core.api.AgentResult;
import com.kset.agent.core.api.AgentRunStatus;
import com.kset.agent.core.checkpoint.AgentCheckpointPort;
import com.kset.agent.core.event.AgentLifecycleListener;
import com.kset.agent.core.model.AgentModel;
import com.kset.agent.core.model.ModelRequest;
import com.kset.agent.core.model.ModelResponse;
import com.kset.agent.core.protocol.AgentDecision;
import com.kset.agent.core.protocol.AgentProtocolCodec;
import com.kset.agent.core.protocol.AgentProtocolContext;
import com.kset.agent.core.protocol.AgentProtocolException;
import com.kset.agent.core.protocol.AgentProtocolRegistry;
import com.kset.agent.core.stop.AgentStopContext;
import com.kset.agent.core.stop.AgentStopController;
import com.kset.agent.core.stop.AgentStopDecision;
import com.kset.agent.core.strategy.AgentReasoningStrategy;
import com.kset.agent.core.strategy.AgentTurn;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Strategy- and protocol-neutral agent loop.
 *
 * <p>The kernel owns ordering and stop guarantees. Strategies choose prompts, codecs translate
 * model protocols, and action handlers perform effects; none of them can bypass a stop decision.
 */
public final class AgentLoopKernel {

    private final AgentModel model;
    private final AgentReasoningStrategy strategy;
    private final AgentProtocolRegistry protocols;
    private final AgentActionRegistry actions;
    private final AgentStopController stops;
    private final AgentCheckpointPort checkpoints;
    private final List<AgentLifecycleListener> listeners;
    private final Clock clock;

    public static AgentKernelBuilder builder(AgentModel model) {
        return new AgentKernelBuilder(model);
    }

    public AgentLoopKernel(AgentModel model,
                           AgentReasoningStrategy strategy,
                           AgentProtocolRegistry protocols,
                           AgentActionRegistry actions,
                           AgentStopController stops,
                           AgentCheckpointPort checkpoints,
                           List<AgentLifecycleListener> listeners,
                           Clock clock) {
        this.model = Objects.requireNonNull(model, "model");
        this.strategy = Objects.requireNonNull(strategy, "strategy");
        this.protocols = Objects.requireNonNull(protocols, "protocols");
        this.actions = Objects.requireNonNull(actions, "actions");
        this.stops = Objects.requireNonNull(stops, "stops");
        this.checkpoints = checkpoints == null ? AgentCheckpointPort.noop() : checkpoints;
        this.listeners = listeners == null ? List.of() : List.copyOf(listeners);
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public AgentResult run(AgentRequest request) {
        Objects.requireNonNull(request, "request");
        return execute(request, AgentRunState.start(request, clock.instant()));
    }

    public AgentResult resume(AgentRequest request) {
        Objects.requireNonNull(request, "request");
        AgentRunSnapshot snapshot = checkpoints.find(request.runId())
                .orElseThrow(() -> new AgentCoreException("agent checkpoint not found: " + request.runId()));
        return resume(request, snapshot);
    }

    public AgentResult resume(AgentRequest request, AgentRunSnapshot snapshot) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(snapshot, "snapshot");
        if (!request.runId().equals(snapshot.runId())) {
            throw new IllegalArgumentException("request and snapshot runId do not match");
        }
        if (!request.protocol().equals(snapshot.protocol())) {
            throw new IllegalArgumentException("protocol cannot change while resuming a run");
        }
        if (!request.task().equals(snapshot.task())) {
            throw new IllegalArgumentException("task cannot change while resuming a run");
        }
        if (snapshot.status() == AgentRunStatus.COMPLETED
                || snapshot.status() == AgentRunStatus.CANCELLED) {
            throw new IllegalArgumentException("completed or cancelled run cannot be resumed");
        }
        return execute(request, AgentRunState.restore(snapshot, clock.instant()));
    }

    private AgentResult execute(AgentRequest request, AgentRunState initialState) {
        AgentRunState state = initialState;
        try {
            notifyListeners(listener -> listener.beforeRun(request, initialState));
            checkpoints.save(state.snapshot());
            while (true) {
                AgentStopDecision beforeTurn = stops.evaluate(stopContext(request, state));
                if (beforeTurn.stop()) {
                    return finish(request, state, beforeTurn);
                }

                state = state.nextTurn(clock.instant());
                AgentRunState turnState = state;
                notifyListeners(listener -> listener.beforeTurn(request, turnState));

                AgentTurn turn = strategy.nextTurn(request, state);
                if (!turn.protocol().equals(state.protocol())) {
                    throw new AgentCoreException("reasoning strategy changed the run protocol");
                }
                AgentProtocolCodec codec = protocols.require(turn.protocol());
                AgentProtocolContext protocolContext = new AgentProtocolContext(request, state);
                ModelRequest modelRequest = codec.prepare(turn.modelRequest(), protocolContext);
                ModelResponse response = model.generate(modelRequest);
                AgentRunState modelState = state;
                notifyListeners(listener -> listener.afterModel(request, modelState, response));

                AgentStopDecision afterModel = stops.evaluateImmediate(stopContext(request, state));
                if (afterModel.stop()) {
                    return finish(request, state, afterModel);
                }

                AgentDecision decision;
                try {
                    decision = codec.decode(response, protocolContext);
                    strategy.validate(request, state, decision);
                } catch (AgentProtocolException protocolError) {
                    state = state.protocolFailed(protocolError.code(), protocolError.getMessage(), clock.instant());
                    AgentRunState failedState = state;
                    notifyListeners(listener -> listener.onProtocolError(request, failedState, protocolError));
                    AgentStopDecision protocolStop = stops.evaluate(stopContext(request, state));
                    if (protocolStop.stop()) {
                        return finish(request, state, protocolStop);
                    }
                    checkpoints.save(state.snapshot());
                    continue;
                }

                state = state.decisionAccepted(decision, clock.instant());
                AgentRunState decisionState = state;
                notifyListeners(listener -> listener.afterDecision(request, decisionState, decision));

                List<AgentActionResult> results = new ArrayList<>();
                for (AgentAction action : decision.actions()) {
                    AgentActionResult result = actions.dispatch(action, new AgentActionContext(request, state));
                    results.add(result);
                    state = state.apply(result, clock.instant());
                    checkpoints.save(state.snapshot());
                    AgentRunState actionState = state;
                    notifyListeners(listener -> listener.afterAction(request, actionState, action, result));
                    if (result.terminalStatus() != null) {
                        break;
                    }
                    AgentStopDecision afterAction = stops.evaluateImmediate(stopContext(request, state));
                    if (afterAction.stop()) {
                        return finish(request, state, afterAction);
                    }
                }

                state = strategy.afterTurn(request, state, decision, List.copyOf(results), clock.instant());
                AgentStopDecision afterTurn = stops.evaluate(stopContext(request, state));
                if (afterTurn.stop()) {
                    return finish(request, state, afterTurn);
                }
                checkpoints.save(state.snapshot());
            }
        } catch (RuntimeException error) {
            return fail(request, state, error);
        }
    }

    private AgentResult finish(AgentRequest request, AgentRunState state, AgentStopDecision decision) {
        AgentRunState stopped = state.stopped(decision, clock.instant());
        checkpoints.save(stopped.snapshot());
        notifyStops(request, stopped, decision);
        return result(stopped);
    }

    private AgentResult fail(AgentRequest request, AgentRunState state, RuntimeException error) {
        AgentStopDecision decision = stops.fatal(error);
        AgentRunState failed = state.stopped(decision, clock.instant());
        notifyErrors(request, failed, error);
        try {
            checkpoints.save(failed.snapshot());
        } catch (RuntimeException ignored) {
            // The returned failed snapshot remains authoritative for the caller.
        }
        notifyStops(request, failed, decision);
        return result(failed);
    }

    private AgentResult result(AgentRunState state) {
        return new AgentResult(state.runId(), state.status(), state.answer(), state.stop(), state.snapshot());
    }

    private AgentStopContext stopContext(AgentRequest request, AgentRunState state) {
        return new AgentStopContext(request, state, clock.instant());
    }

    private void notifyListeners(Consumer<AgentLifecycleListener> notification) {
        for (AgentLifecycleListener listener : listeners) {
            try {
                notification.accept(listener);
            } catch (RuntimeException error) {
                if (listener.critical()) {
                    throw error;
                }
            }
        }
    }

    private void notifyErrors(AgentRequest request, AgentRunState state, RuntimeException error) {
        listeners.forEach(listener -> {
            try {
                listener.onError(request, state, error);
            } catch (RuntimeException ignored) {
            }
        });
    }

    private void notifyStops(AgentRequest request, AgentRunState state, AgentStopDecision decision) {
        listeners.forEach(listener -> {
            try {
                listener.onStop(request, state, decision);
            } catch (RuntimeException ignored) {
            }
        });
    }
}
