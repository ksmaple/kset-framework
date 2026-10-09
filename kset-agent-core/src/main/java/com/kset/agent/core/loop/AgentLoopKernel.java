package com.kset.agent.core.loop;

import com.kset.agent.core.AgentCoreException;
import com.kset.agent.core.AgentErrorCode;
import com.kset.agent.core.action.AgentAction;
import com.kset.agent.core.action.AgentActionContext;
import com.kset.agent.core.action.AgentActionRegistry;
import com.kset.agent.core.action.AgentActionResult;
import com.kset.agent.core.action.ToolBatchAction;
import com.kset.agent.core.action.ToolCallAction;
import com.kset.agent.core.api.AgentFailure;
import com.kset.agent.core.api.AgentRequest;
import com.kset.agent.core.api.AgentResult;
import com.kset.agent.core.api.AgentRunStatus;
import com.kset.agent.core.checkpoint.AgentCheckpointPort;
import com.kset.agent.core.event.AgentLifecycleListener;
import com.kset.agent.core.execution.AgentExecutionContext;
import com.kset.agent.core.model.AgentModel;
import com.kset.agent.core.model.ModelRequest;
import com.kset.agent.core.model.ModelResponse;
import com.kset.agent.core.protocol.AgentDecision;
import com.kset.agent.core.protocol.AgentProtocolCodec;
import com.kset.agent.core.protocol.AgentProtocolContext;
import com.kset.agent.core.protocol.AgentProtocolException;
import com.kset.agent.core.protocol.AgentProtocolId;
import com.kset.agent.core.protocol.AgentProtocolRegistry;
import com.kset.agent.core.stop.AgentStopContext;
import com.kset.agent.core.stop.AgentStopController;
import com.kset.agent.core.stop.AgentStopDecision;
import com.kset.agent.core.stop.AgentStopReason;
import com.kset.agent.core.strategy.AgentReasoningStrategy;
import com.kset.agent.core.strategy.AgentTurn;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Strategy- and protocol-neutral agent loop.
 *
 * <p>The kernel owns ordering and stop guarantees. Strategies choose prompts, codecs translate
 * model protocols, and action handlers perform effects; none of them can bypass a stop decision.
 * The kernel is safe to share only when all supplied collaborators are thread-safe.
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

    /** Returns the immutable protocol identities available to new runs. */
    public List<AgentProtocolId> supportedProtocols() {
        return protocols.supportedProtocols();
    }

    AgentLoopKernel(AgentModel model,
                    AgentReasoningStrategy strategy,
                    AgentProtocolRegistry protocols,
                    AgentActionRegistry actions,
                    AgentStopController stops,
                    AgentCheckpointPort checkpoints,
                    List<AgentLifecycleListener> listeners,
                    Clock clock) {
        this.model = requireConfiguration(model, "model");
        this.strategy = requireConfiguration(strategy, "strategy");
        this.protocols = requireConfiguration(protocols, "protocols");
        this.actions = requireConfiguration(actions, "actions");
        this.stops = requireConfiguration(stops, "stops");
        this.checkpoints = checkpoints == null ? AgentCheckpointPort.noop() : checkpoints;
        try {
            this.listeners = listeners == null ? List.of() : List.copyOf(listeners);
        } catch (RuntimeException error) {
            throw new AgentCoreException(AgentErrorCode.INVALID_CONFIGURATION,
                    "listeners must not contain null", error);
        }
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public AgentResult run(AgentRequest request) {
        if (request == null) {
            throw new AgentCoreException(AgentErrorCode.INVALID_REQUEST,
                    "request must not be null");
        }
        protocols.require(request.protocol());
        return execute(request, AgentRunState.start(request, clock.instant()));
    }

    public AgentResult resume(AgentRequest request, AgentRunSnapshot snapshot) {
        if (request == null) {
            throw new AgentCoreException(AgentErrorCode.INVALID_REQUEST,
                    "request must not be null");
        }
        if (snapshot == null) {
            throw invalidSnapshot("snapshot must not be null");
        }
        if (!request.runId().equals(snapshot.runId())) {
            throw invalidSnapshot("request and snapshot runId do not match");
        }
        if (!request.protocol().equals(snapshot.protocol())) {
            throw invalidSnapshot("protocol cannot change while resuming a run");
        }
        if (!request.task().equals(snapshot.task())) {
            throw invalidSnapshot("task cannot change while resuming a run");
        }
        if (snapshot.status() == AgentRunStatus.COMPLETED
                || snapshot.status() == AgentRunStatus.CANCELLED) {
            throw invalidSnapshot("completed or cancelled run cannot be resumed");
        }
        protocols.require(request.protocol());
        return execute(request, AgentRunState.restore(snapshot, clock.instant()));
    }

    private AgentResult execute(AgentRequest request, AgentRunState initialState) {
        AgentRunState state = initialState;
        Instant executionStartedAt = clock.instant();
        Instant deadline;
        try {
            deadline = executionStartedAt.plus(request.options().timeout());
        } catch (RuntimeException error) {
            return fail(request, state, error);
        }
        try {
            notifyListeners(listener -> listener.beforeRun(request, initialState));
            saveCheckpoint(request, state);
            while (true) {
                AgentStopDecision beforeTurn = stops.evaluate(
                        stopContext(request, state, executionStartedAt, deadline));
                if (beforeTurn.stop()) {
                    return finish(request, state, beforeTurn);
                }

                state = state.nextTurn(clock.instant());
                AgentRunState turnState = state;
                notifyListeners(listener -> listener.beforeTurn(request, turnState));

                AgentTurn turn = nextTurn(request, state);
                if (!turn.protocol().equals(state.protocol())) {
                    throw new AgentCoreException(AgentErrorCode.EXTENSION_CONTRACT_VIOLATION,
                            "reasoning strategy changed the run protocol");
                }
                AgentProtocolCodec codec = protocols.require(turn.protocol());
                AgentProtocolContext protocolContext = new AgentProtocolContext(request, state);
                ModelRequest modelRequest = prepareModelRequest(
                        codec, turn.modelRequest(), protocolContext);
                AgentStopDecision beforeModel = stops.evaluateImmediate(
                        stopContext(request, state, executionStartedAt, deadline));
                if (beforeModel.stop()) {
                    return finish(request, state, beforeModel);
                }
                ModelResponse response = generateModel(modelRequest,
                        executionContext(request, state, executionStartedAt, deadline));

                AgentStopDecision afterModelExecution = stops.evaluateImmediate(
                        stopContext(request, state, executionStartedAt, deadline));
                if (afterModelExecution.stop()) {
                    return finish(request, state, afterModelExecution);
                }
                AgentRunState modelState = state;
                notifyListeners(listener -> listener.afterModel(request, modelState, response));

                AgentStopDecision afterModel = stops.evaluateImmediate(
                        stopContext(request, state, executionStartedAt, deadline));
                if (afterModel.stop()) {
                    return finish(request, state, afterModel);
                }

                AgentDecision decision;
                try {
                    decision = decode(codec, response, protocolContext);
                    validateToolCallIds(decision);
                    AgentStopDecision capacityStop = capacityStop(request, decision);
                    if (capacityStop.stop()) {
                        return finish(request, state, capacityStop);
                    }
                    validateDecision(request, state, decision);
                } catch (AgentProtocolException protocolError) {
                    state = state.protocolFailed(
                            protocolError.protocolCode(), protocolError.getMessage(), clock.instant());
                    AgentRunState failedState = state;
                    notifyListeners(listener -> listener.onProtocolError(request, failedState, protocolError));
                    AgentStopDecision protocolStop = stops.evaluate(
                            stopContext(request, state, executionStartedAt, deadline));
                    if (protocolStop.stop()) {
                        return finish(request, state, protocolStop);
                    }
                    saveCheckpoint(request, state);
                    continue;
                }

                AgentStopDecision afterDecision = stops.evaluateImmediate(
                        stopContext(request, state, executionStartedAt, deadline));
                if (afterDecision.stop()) {
                    return finish(request, state, afterDecision);
                }

                state = state.decisionAccepted(decision, clock.instant());
                AgentRunState decisionState = state;
                notifyListeners(listener -> listener.afterDecision(request, decisionState, decision));

                List<AgentActionResult> results = new ArrayList<>();
                for (AgentAction action : decision.actions()) {
                    AgentStopDecision beforeAction = stops.evaluateImmediate(
                            stopContext(request, state, executionStartedAt, deadline));
                    if (beforeAction.stop()) {
                        return finish(request, state, beforeAction);
                    }
                    AgentActionResult result = dispatchAction(action, new AgentActionContext(
                            request, state,
                            executionContext(request, state, executionStartedAt, deadline), clock));

                    AgentStopDecision afterActionExecution = stops.evaluateImmediate(
                            stopContext(request, state, executionStartedAt, deadline));
                    if (afterActionExecution.stop()) {
                        return finish(request, state, afterActionExecution);
                    }
                    results.add(result);
                    state = state.apply(result, clock.instant());
                    AgentRunState actionState = state;
                    notifyListeners(listener -> listener.afterAction(request, actionState, action, result));
                    if (result.terminalStatus() != null) {
                        AgentStopDecision terminalStop = stops.evaluate(
                                stopContext(request, state, executionStartedAt, deadline));
                        if (!terminalStop.stop()) {
                            throw new AgentCoreException(
                                    AgentErrorCode.EXTENSION_CONTRACT_VIOLATION,
                                    "terminal action result did not produce a stop decision");
                        }
                        return finish(request, state, terminalStop);
                    }

                    saveCheckpoint(request, state);
                    AgentStopDecision afterAction = stops.evaluateAfterAction(
                            stopContext(request, state, executionStartedAt, deadline));
                    if (afterAction.stop()) {
                        return finish(request, state, afterAction);
                    }
                }

                AgentRunState nextState = afterTurn(
                        request, state, decision, List.copyOf(results), clock.instant());
                validateStrategyState(state, nextState);
                state = nextState;
                AgentStopDecision afterTurn = stops.evaluate(
                        stopContext(request, state, executionStartedAt, deadline));
                if (afterTurn.stop()) {
                    return finish(request, state, afterTurn);
                }
                saveCheckpoint(request, state);
            }
        } catch (RuntimeException error) {
            return stopOrFail(request, state, executionStartedAt, deadline, error);
        }
    }

    private AgentResult stopOrFail(
            AgentRequest request, AgentRunState state,
            Instant executionStartedAt, Instant deadline, RuntimeException error) {
        try {
            AgentStopDecision immediate = stops.evaluateImmediate(
                    stopContext(request, state, executionStartedAt, deadline));
            return immediate.stop()
                    ? finish(request, state, immediate)
                    : fail(request, state, error);
        } catch (RuntimeException stopError) {
            return fail(request, state, stopError);
        }
    }

    private AgentResult finish(AgentRequest request, AgentRunState state, AgentStopDecision decision) {
        AgentRunState stopped = state.stopped(decision, clock.instant());
        saveCheckpoint(request, stopped);
        notifyStops(request, stopped, decision);
        return result(stopped, null);
    }

    private AgentResult fail(AgentRequest request, AgentRunState state, RuntimeException error) {
        AgentStopDecision decision = stops.fatal(error);
        AgentRunState failed = state.stopped(decision, clock.instant());
        notifyErrors(request, failed, error);
        try {
            checkpoints.save(request, failed.snapshot());
        } catch (RuntimeException ignored) {
            // The returned failed snapshot remains authoritative for the caller.
        }
        notifyStops(request, failed, decision);
        return result(failed, failure(error));
    }

    private AgentResult result(AgentRunState state, AgentFailure failure) {
        return new AgentResult(state.runId(), state.status(), state.answer(), state.stop(),
                state.snapshot(), failure);
    }

    private AgentCoreException invalidSnapshot(String message) {
        return new AgentCoreException(AgentErrorCode.INVALID_SNAPSHOT, message);
    }

    private void saveCheckpoint(AgentRequest request, AgentRunState state) {
        try {
            checkpoints.save(request, state.snapshot());
        } catch (AgentCoreException error) {
            throw error;
        } catch (RuntimeException error) {
            throw new AgentCoreException(AgentErrorCode.CHECKPOINT_FAILED,
                    "checkpoint save failed: " + errorMessage(error), error);
        }
    }

    private AgentTurn nextTurn(AgentRequest request, AgentRunState state) {
        try {
            AgentTurn turn = strategy.nextTurn(request, state);
            if (turn == null) {
                throw new AgentCoreException(AgentErrorCode.EXTENSION_CONTRACT_VIOLATION,
                        "reasoning strategy returned null turn");
            }
            return turn;
        } catch (AgentCoreException error) {
            throw error;
        } catch (RuntimeException error) {
            throw new AgentCoreException(AgentErrorCode.STRATEGY_EXECUTION_FAILED,
                    "reasoning strategy failed: " + errorMessage(error), error);
        }
    }

    private ModelRequest prepareModelRequest(
            AgentProtocolCodec codec, ModelRequest request, AgentProtocolContext context) {
        try {
            ModelRequest prepared = codec.prepare(request, context);
            if (prepared == null) {
                throw new AgentCoreException(AgentErrorCode.EXTENSION_CONTRACT_VIOLATION,
                        "protocol codec returned null model request");
            }
            return prepared;
        } catch (AgentCoreException error) {
            throw error;
        } catch (RuntimeException error) {
            throw new AgentCoreException(AgentErrorCode.PROTOCOL_CODEC_FAILED,
                    "protocol request preparation failed: " + errorMessage(error), error);
        }
    }

    private ModelResponse generateModel(
            ModelRequest request, AgentExecutionContext context) {
        try {
            ModelResponse response = model.generate(request, context);
            if (response == null) {
                throw new AgentCoreException(AgentErrorCode.EXTENSION_CONTRACT_VIOLATION,
                        "model returned null response");
            }
            return response;
        } catch (AgentCoreException error) {
            throw error;
        } catch (RuntimeException error) {
            throw new AgentCoreException(AgentErrorCode.MODEL_CALL_FAILED,
                    "model call failed: " + errorMessage(error), error);
        }
    }

    private AgentDecision decode(
            AgentProtocolCodec codec, ModelResponse response, AgentProtocolContext context) {
        try {
            AgentDecision decision = codec.decode(response, context);
            if (decision == null) {
                throw new AgentCoreException(AgentErrorCode.EXTENSION_CONTRACT_VIOLATION,
                        "protocol codec returned null decision");
            }
            return decision;
        } catch (AgentProtocolException error) {
            throw error;
        } catch (AgentCoreException error) {
            throw error;
        } catch (RuntimeException error) {
            throw new AgentCoreException(AgentErrorCode.PROTOCOL_CODEC_FAILED,
                    "protocol response decoding failed: " + errorMessage(error), error);
        }
    }

    private void validateDecision(
            AgentRequest request, AgentRunState state, AgentDecision decision) {
        try {
            strategy.validate(request, state, decision);
        } catch (AgentProtocolException error) {
            throw error;
        } catch (AgentCoreException error) {
            throw error;
        } catch (RuntimeException error) {
            throw new AgentCoreException(AgentErrorCode.STRATEGY_EXECUTION_FAILED,
                    "reasoning strategy validation failed: " + errorMessage(error), error);
        }
    }

    private AgentActionResult dispatchAction(
            AgentAction action, AgentActionContext context) {
        try {
            AgentActionResult result = actions.dispatch(action, context);
            if (result == null) {
                throw new AgentCoreException(AgentErrorCode.EXTENSION_CONTRACT_VIOLATION,
                        "action handler returned null result");
            }
            if (result.terminalStatus() != null
                    && result.terminalStatus() != AgentRunStatus.COMPLETED
                    && result.terminalStatus() != AgentRunStatus.SUSPENDED) {
                throw new AgentCoreException(AgentErrorCode.EXTENSION_CONTRACT_VIOLATION,
                        "action handler terminal status must be COMPLETED or SUSPENDED");
            }
            if (result.terminalStatus() == AgentRunStatus.COMPLETED
                    && (result.answer() == null || result.answer().isBlank())) {
                throw new AgentCoreException(AgentErrorCode.EXTENSION_CONTRACT_VIOLATION,
                        "completed action result must provide a non-blank answer");
            }
            return result;
        } catch (AgentCoreException error) {
            throw error;
        } catch (RuntimeException error) {
            throw new AgentCoreException(AgentErrorCode.ACTION_EXECUTION_FAILED,
                    "action execution failed: " + errorMessage(error), error);
        }
    }

    private AgentRunState afterTurn(
            AgentRequest request, AgentRunState state, AgentDecision decision,
            List<AgentActionResult> results, Instant now) {
        try {
            AgentRunState next = strategy.afterTurn(request, state, decision, results, now);
            if (next == null) {
                throw new AgentCoreException(AgentErrorCode.EXTENSION_CONTRACT_VIOLATION,
                        "reasoning strategy returned null run state");
            }
            return next;
        } catch (AgentCoreException error) {
            throw error;
        } catch (RuntimeException error) {
            throw new AgentCoreException(AgentErrorCode.STRATEGY_EXECUTION_FAILED,
                    "reasoning strategy afterTurn failed: " + errorMessage(error), error);
        }
    }

    private AgentExecutionContext executionContext(
            AgentRequest request, AgentRunState state, Instant executionStartedAt, Instant deadline) {
        return new AgentExecutionContext(request.runId(), state.turn(), executionStartedAt,
                clock.instant(), deadline, request.cancellation(), request.attributes());
    }

    private AgentStopContext stopContext(
            AgentRequest request, AgentRunState state, Instant executionStartedAt, Instant deadline) {
        AgentExecutionContext execution = executionContext(
                request, state, executionStartedAt, deadline);
        return new AgentStopContext(request, state, execution, execution.issuedAt());
    }

    private AgentStopDecision capacityStop(AgentRequest request, AgentDecision decision) {
        if (decision.actions().size() > request.options().maxActionsPerDecision()) {
            return AgentStopDecision.stop(AgentStopReason.CAPACITY_LIMIT, AgentRunStatus.FAILED,
                    "decision action limit exceeded");
        }
        boolean oversizedBatch = decision.actions().stream()
                .filter(ToolBatchAction.class::isInstance)
                .map(ToolBatchAction.class::cast)
                .anyMatch(batch -> batch.calls().size() > request.options().maxToolCallsPerBatch());
        if (oversizedBatch) {
            return AgentStopDecision.stop(AgentStopReason.CAPACITY_LIMIT, AgentRunStatus.FAILED,
                    "tool batch size limit exceeded");
        }
        return AgentStopDecision.continueRun();
    }

    private void validateToolCallIds(AgentDecision decision) {
        Set<String> callIds = new HashSet<>();
        for (AgentAction action : decision.actions()) {
            if (action instanceof ToolCallAction call
                    && !callIds.add(call.callId())) {
                throw new AgentProtocolException(
                        "DUPLICATE_TOOL_CALL_ID", "tool call ids must be unique within a decision");
            }
            if (action instanceof ToolBatchAction batch) {
                for (ToolCallAction call : batch.calls()) {
                    if (!callIds.add(call.callId())) {
                        throw new AgentProtocolException(
                                "DUPLICATE_TOOL_CALL_ID",
                                "tool call ids must be unique within a decision");
                    }
                }
            }
        }
    }

    private void validateStrategyState(AgentRunState current, AgentRunState next) {
        boolean coreStateChanged = !current.runId().equals(next.runId())
                || !current.task().equals(next.task())
                || !current.protocol().equals(next.protocol())
                || current.status() != next.status()
                || current.turn() != next.turn()
                || !current.startedAt().equals(next.startedAt())
                || next.updatedAt().isBefore(current.updatedAt())
                || !current.observations().equals(next.observations())
                || current.consecutiveProtocolErrors() != next.consecutiveProtocolErrors()
                || current.consecutiveNoProgress() != next.consecutiveNoProgress()
                || !Objects.equals(current.answer(), next.answer())
                || !Objects.equals(current.stop(), next.stop());
        if (coreStateChanged) {
            throw new AgentCoreException(AgentErrorCode.EXTENSION_CONTRACT_VIOLATION,
                    "reasoning strategy changed kernel-owned run state");
        }
    }

    private void notifyListeners(Consumer<AgentLifecycleListener> notification) {
        for (AgentLifecycleListener listener : listeners) {
            try {
                notification.accept(listener);
            } catch (RuntimeException error) {
                if (listener.critical()) {
                    if (error instanceof AgentCoreException coreError) {
                        throw coreError;
                    }
                    throw new AgentCoreException(AgentErrorCode.LISTENER_FAILED,
                            "critical lifecycle listener failed: "
                                    + listener.getClass().getName() + ": "
                                    + errorMessage(error), error);
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

    private AgentFailure failure(RuntimeException error) {
        if (error instanceof AgentCoreException coreError) {
            return new AgentFailure(
                    coreError.code(), errorMessage(coreError), coreError.retryable());
        }
        return new AgentFailure(AgentErrorCode.INTERNAL_ERROR, errorMessage(error), false);
    }

    private String errorMessage(Throwable error) {
        return error.getMessage() == null || error.getMessage().isBlank()
                ? error.getClass().getSimpleName() : error.getMessage();
    }

    private static <T> T requireConfiguration(T value, String name) {
        if (value == null) {
            throw new AgentCoreException(AgentErrorCode.INVALID_CONFIGURATION,
                    name + " must not be null");
        }
        return value;
    }
}
