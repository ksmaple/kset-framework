package com.kset.agent.core.loop;

import com.kset.agent.core.AgentCoreException;
import com.kset.agent.core.AgentErrorCode;
import com.kset.agent.core.action.AgentAction;
import com.kset.agent.core.action.AgentActionContext;
import com.kset.agent.core.action.AgentActionRegistry;
import com.kset.agent.core.action.AgentActionResult;
import com.kset.agent.core.action.StandardActionTypes;
import com.kset.agent.core.action.ToolBatchAction;
import com.kset.agent.core.action.ToolCallAction;
import com.kset.agent.core.api.AgentFailure;
import com.kset.agent.core.api.AgentRequest;
import com.kset.agent.core.api.AgentResult;
import com.kset.agent.core.api.AgentRunStatus;
import com.kset.agent.core.checkpoint.AgentCheckpointPort;
import com.kset.agent.core.event.AgentInvocationType;
import com.kset.agent.core.event.AgentLifecycleContext;
import com.kset.agent.core.event.AgentLifecycleEventType;
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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Strategy- and protocol-neutral agent loop.
 *
 * <p>The kernel owns ordering and stop guarantees. Strategies choose prompts, codecs translate
 * model protocols, and action handlers perform effects; none of them can bypass a stop decision.
 * The kernel is safe to share only when all supplied collaborators are thread-safe.
 */
public final class AgentLoopKernel {

    private static final Set<String> STANDARD_ACTION_TYPES = Set.of(
            StandardActionTypes.TASK_PLAN,
            StandardActionTypes.TOOL_CALL,
            StandardActionTypes.TOOL_BATCH,
            StandardActionTypes.ANSWER_CHUNK,
            StandardActionTypes.FINAL_ANSWER,
            StandardActionTypes.CONFIRMATION);

    private final AgentModel model;
    private final AgentReasoningStrategy strategy;
    private final String strategyAttributePrefix;
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
                    String strategyId,
                    AgentProtocolRegistry protocols,
                    AgentActionRegistry actions,
                    AgentStopController stops,
                    AgentCheckpointPort checkpoints,
                    List<AgentLifecycleListener> listeners,
                    Clock clock) {
        this.model = requireConfiguration(model, "model");
        this.strategy = requireConfiguration(strategy, "strategy");
        this.strategyAttributePrefix = requireConfiguration(strategyId, "strategyId") + ".";
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
        return execute(request, AgentRunState.start(request, clock.instant()), AgentInvocationType.RUN);
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
        return execute(request, AgentRunState.restore(snapshot, clock.instant()),
                AgentInvocationType.RESUME);
    }

    private AgentResult execute(AgentRequest request, AgentRunState initialState,
                                AgentInvocationType invocationType) {
        AgentRunState state = initialState;
        Instant executionStartedAt = clock.instant();
        Instant deadline;
        try {
            deadline = executionStartedAt.plus(request.options().timeout());
        } catch (RuntimeException error) {
            LifecycleEvents events = new LifecycleEvents(
                    invocationType, request.runId(), executionStartedAt, Instant.MAX);
            return fail(request, state, error, events);
        }
        LifecycleEvents events = new LifecycleEvents(
                invocationType, request.runId(), executionStartedAt, deadline);
        try {
            AgentLifecycleContext runStarted = events.next(
                    AgentLifecycleEventType.RUN_STARTED, state, AgentLifecycleContext.NO_ACTION,
                    clock.instant(), Duration.ZERO);
            notifyListeners(runStarted,
                    listener -> listener.beforeRun(runStarted, request, initialState));
            saveCheckpoint(request, state, events);
            while (true) {
                AgentStopDecision beforeTurn = stops.evaluate(
                        stopContext(request, state, executionStartedAt, deadline));
                if (beforeTurn.stop()) {
                    return finish(request, state, beforeTurn, events);
                }

                state = state.nextTurn(clock.instant());
                AgentRunState turnState = state;
                Instant turnStartedAt = clock.instant();
                AgentLifecycleContext turnStarted = events.next(
                        AgentLifecycleEventType.TURN_STARTED, state,
                        AgentLifecycleContext.NO_ACTION, turnStartedAt, Duration.ZERO);
                notifyListeners(turnStarted,
                        listener -> listener.beforeTurn(turnStarted, request, turnState));

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
                    return finish(request, state, beforeModel, events);
                }
                Instant modelStartedAt = clock.instant();
                AgentLifecycleContext modelStarted = events.next(
                        AgentLifecycleEventType.MODEL_STARTED, state,
                        AgentLifecycleContext.NO_ACTION, modelStartedAt, Duration.ZERO);
                notifyListeners(modelStarted, listener -> listener.beforeModel(
                        modelStarted, request, turnState, modelRequest));
                AgentStopDecision afterBeforeModel = stops.evaluateImmediate(
                        stopContext(request, state, executionStartedAt, deadline));
                if (afterBeforeModel.stop()) {
                    return finish(request, state, afterBeforeModel, events);
                }
                Instant modelExecutionStartedAt = clock.instant();
                ModelResponse response = generateModel(modelRequest,
                        executionContext(request, state, executionStartedAt, deadline));

                Instant modelCompletedAt = clock.instant();
                AgentRunState modelState = state;
                AgentLifecycleContext modelCompleted = events.next(
                        AgentLifecycleEventType.MODEL_COMPLETED, state,
                        AgentLifecycleContext.NO_ACTION, modelCompletedAt,
                        elapsed(modelExecutionStartedAt, modelCompletedAt));
                notifyListeners(modelCompleted, listener -> listener.afterModel(
                        modelCompleted, request, modelState, response));

                AgentStopDecision afterModelExecution = stops.evaluateImmediate(
                        stopContext(request, state, executionStartedAt, deadline));
                if (afterModelExecution.stop()) {
                    return finish(request, state, afterModelExecution, events);
                }

                AgentDecision decision;
                try {
                    decision = decode(codec, response, protocolContext);
                    validateToolCallIds(decision);
                    AgentStopDecision capacityStop = capacityStop(request, decision);
                    if (capacityStop.stop()) {
                        return finish(request, state, capacityStop, events);
                    }
                    validateDecision(request, state, decision);
                } catch (AgentProtocolException protocolError) {
                    state = state.protocolFailed(
                            protocolError.protocolCode(), protocolError.getMessage(), clock.instant());
                    AgentRunState failedState = state;
                    AgentLifecycleContext protocolFailed = events.next(
                            AgentLifecycleEventType.PROTOCOL_ERROR, state,
                            AgentLifecycleContext.NO_ACTION, clock.instant(), Duration.ZERO);
                    notifyListeners(protocolFailed, listener -> listener.onProtocolError(
                            protocolFailed, request, failedState, protocolError));
                    AgentStopDecision protocolStop = stops.evaluate(
                            stopContext(request, state, executionStartedAt, deadline));
                    if (protocolStop.stop()) {
                        return finish(request, state, protocolStop, events);
                    }
                    saveCheckpoint(request, state, events);
                    continue;
                }

                AgentStopDecision afterDecision = stops.evaluateImmediate(
                        stopContext(request, state, executionStartedAt, deadline));
                if (afterDecision.stop()) {
                    return finish(request, state, afterDecision, events);
                }

                state = state.decisionAccepted(decision, clock.instant());
                AgentRunState decisionState = state;
                AgentLifecycleContext decisionAccepted = events.next(
                        AgentLifecycleEventType.DECISION_ACCEPTED, state,
                        AgentLifecycleContext.NO_ACTION, clock.instant(), Duration.ZERO);
                notifyListeners(decisionAccepted, listener -> listener.afterDecision(
                        decisionAccepted, request, decisionState, decision));

                List<AgentActionResult> results = new ArrayList<>();
                for (int actionIndex = 0; actionIndex < decision.actions().size(); actionIndex++) {
                    AgentAction action = decision.actions().get(actionIndex);
                    AgentStopDecision beforeAction = stops.evaluateImmediate(
                            stopContext(request, state, executionStartedAt, deadline));
                    if (beforeAction.stop()) {
                        return finish(request, state, beforeAction, events);
                    }
                    Instant actionStartedAt = clock.instant();
                    AgentRunState beforeActionState = state;
                    int currentActionIndex = actionIndex;
                    AgentLifecycleContext actionStarted = events.next(
                            AgentLifecycleEventType.ACTION_STARTED, state, currentActionIndex,
                            actionStartedAt, Duration.ZERO);
                    notifyListeners(actionStarted, listener -> listener.beforeAction(
                            actionStarted, request, beforeActionState, action));
                    AgentStopDecision afterBeforeAction = stops.evaluateImmediate(
                            stopContext(request, state, executionStartedAt, deadline));
                    if (afterBeforeAction.stop()) {
                        return finish(request, state, afterBeforeAction, events);
                    }
                    Instant actionExecutionStartedAt = clock.instant();
                    AgentActionResult result = dispatchAction(action, new AgentActionContext(
                            request, state,
                            executionContext(request, state, executionStartedAt, deadline), clock));

                    results.add(result);
                    state = state.apply(result, clock.instant());
                    AgentRunState actionState = state;
                    Instant actionCompletedAt = clock.instant();
                    AgentLifecycleContext actionCompleted = events.next(
                            AgentLifecycleEventType.ACTION_COMPLETED, state, currentActionIndex,
                            actionCompletedAt, elapsed(actionExecutionStartedAt, actionCompletedAt));
                    notifyListeners(actionCompleted, listener -> listener.afterAction(
                            actionCompleted, request, actionState, action, result));

                    AgentStopDecision afterActionExecution = stops.evaluateImmediate(
                            stopContext(request, state, executionStartedAt, deadline));
                    if (afterActionExecution.stop()) {
                        return finish(request, state, afterActionExecution, events);
                    }
                    if (result.terminalStatus() != null) {
                        AgentStopDecision terminalStop = stops.evaluate(
                                stopContext(request, state, executionStartedAt, deadline));
                        if (!terminalStop.stop()) {
                            throw new AgentCoreException(
                                    AgentErrorCode.EXTENSION_CONTRACT_VIOLATION,
                                    "terminal action result did not produce a stop decision");
                        }
                        return finish(request, state, terminalStop, events);
                    }

                    saveCheckpoint(request, state, events);
                    AgentStopDecision afterAction = stops.evaluateAfterAction(
                            stopContext(request, state, executionStartedAt, deadline));
                    if (afterAction.stop()) {
                        return finish(request, state, afterAction, events);
                    }
                }

                AgentRunState nextState = afterTurn(
                        request, state, decision, List.copyOf(results), clock.instant());
                validateStrategyState(state, nextState);
                state = nextState;
                AgentRunState completedTurnState = state;
                List<AgentActionResult> completedResults = List.copyOf(results);
                Instant turnCompletedAt = clock.instant();
                AgentLifecycleContext turnCompleted = events.next(
                        AgentLifecycleEventType.TURN_COMPLETED, state,
                        AgentLifecycleContext.NO_ACTION, turnCompletedAt,
                        elapsed(turnStartedAt, turnCompletedAt));
                notifyListeners(turnCompleted, listener -> listener.afterTurn(
                        turnCompleted, request, completedTurnState, decision, completedResults));
                AgentStopDecision afterTurn = stops.evaluate(
                        stopContext(request, state, executionStartedAt, deadline));
                if (afterTurn.stop()) {
                    return finish(request, state, afterTurn, events);
                }
                saveCheckpoint(request, state, events);
            }
        } catch (RuntimeException error) {
            return stopOrFail(request, state, executionStartedAt, deadline, error, events);
        }
    }

    private AgentResult stopOrFail(
            AgentRequest request, AgentRunState state,
            Instant executionStartedAt, Instant deadline, RuntimeException error,
            LifecycleEvents events) {
        try {
            AgentStopDecision immediate = stops.evaluateImmediate(
                    stopContext(request, state, executionStartedAt, deadline));
            return immediate.stop()
                    ? finish(request, state, immediate, events)
                    : fail(request, state, error, events);
        } catch (RuntimeException stopError) {
            return fail(request, state, stopError, events);
        }
    }

    private AgentResult finish(AgentRequest request, AgentRunState state,
                               AgentStopDecision decision, LifecycleEvents events) {
        AgentRunState stopped = state.stopped(decision, clock.instant());
        saveCheckpoint(request, stopped, events);
        notifyStops(request, stopped, decision, events);
        AgentResult result = result(stopped, null);
        notifyResult(request, stopped, result, events);
        return result;
    }

    private AgentResult fail(AgentRequest request, AgentRunState state,
                             RuntimeException error, LifecycleEvents events) {
        AgentStopDecision decision = stops.fatal(error);
        AgentRunState failed = state.stopped(decision, clock.instant());
        notifyErrors(request, failed, error, events);
        saveFailureCheckpoint(request, failed, events);
        notifyStops(request, failed, decision, events);
        AgentResult result = result(failed, failure(error));
        notifyResult(request, failed, result, events);
        return result;
    }

    private AgentResult result(AgentRunState state, AgentFailure failure) {
        return new AgentResult(state.runId(), state.status(), state.answer(), state.stop(),
                state.snapshot(), failure);
    }

    private AgentCoreException invalidSnapshot(String message) {
        return new AgentCoreException(AgentErrorCode.INVALID_SNAPSHOT, message);
    }

    private void saveCheckpoint(AgentRequest request, AgentRunState state, LifecycleEvents events) {
        AgentRunSnapshot snapshot = state.snapshot();
        Instant checkpointRequestedAt = clock.instant();
        AgentLifecycleContext saving = events.next(
                AgentLifecycleEventType.CHECKPOINT_SAVING, state,
                AgentLifecycleContext.NO_ACTION, checkpointRequestedAt, Duration.ZERO);
        notifyListeners(saving, listener -> listener.beforeCheckpoint(saving, request, snapshot));
        Instant checkpointExecutionStartedAt = clock.instant();
        try {
            checkpoints.save(request, snapshot);
        } catch (AgentCoreException error) {
            throw error;
        } catch (RuntimeException error) {
            throw new AgentCoreException(AgentErrorCode.CHECKPOINT_FAILED,
                    "checkpoint save failed: " + errorMessage(error), error);
        }
        Instant checkpointCompletedAt = clock.instant();
        AgentLifecycleContext saved = events.next(
                AgentLifecycleEventType.CHECKPOINT_SAVED, state,
                AgentLifecycleContext.NO_ACTION, checkpointCompletedAt,
                elapsed(checkpointExecutionStartedAt, checkpointCompletedAt));
        notifyListeners(saved, listener -> listener.afterCheckpoint(saved, request, snapshot));
    }

    private void saveFailureCheckpoint(
            AgentRequest request, AgentRunState state, LifecycleEvents events) {
        AgentRunSnapshot snapshot = state.snapshot();
        Instant checkpointRequestedAt = clock.instant();
        AgentLifecycleContext saving = events.next(
                AgentLifecycleEventType.CHECKPOINT_SAVING, state,
                AgentLifecycleContext.NO_ACTION, checkpointRequestedAt, Duration.ZERO);
        notifyTerminalListeners(saving,
                listener -> listener.beforeCheckpoint(saving, request, snapshot));
        Instant checkpointExecutionStartedAt = clock.instant();
        try {
            checkpoints.save(request, snapshot);
            Instant checkpointCompletedAt = clock.instant();
            AgentLifecycleContext saved = events.next(
                    AgentLifecycleEventType.CHECKPOINT_SAVED, state,
                    AgentLifecycleContext.NO_ACTION, checkpointCompletedAt,
                    elapsed(checkpointExecutionStartedAt, checkpointCompletedAt));
            notifyTerminalListeners(saved,
                    listener -> listener.afterCheckpoint(saved, request, snapshot));
        } catch (RuntimeException ignored) {
            // The returned failed snapshot remains authoritative for the caller.
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
            validateActionStateChanges(action, result);
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
        Set<String> attributeKeys = new HashSet<>(current.attributes().keySet());
        attributeKeys.addAll(next.attributes().keySet());
        for (String key : attributeKeys) {
            if (!Objects.equals(current.attributes().get(key), next.attributes().get(key))
                    && !key.startsWith(strategyAttributePrefix)) {
                throw new AgentCoreException(AgentErrorCode.EXTENSION_CONTRACT_VIOLATION,
                        "reasoning strategy changed attribute outside its namespace: " + key);
            }
        }
    }

    private void validateActionStateChanges(AgentAction action, AgentActionResult result) {
        Set<String> changedKeys = new HashSet<>(result.stateAttributes().keySet());
        for (String key : result.removedStateAttributes()) {
            if (!changedKeys.add(key)) {
                throw new AgentCoreException(AgentErrorCode.EXTENSION_CONTRACT_VIOLATION,
                        "action result cannot write and remove the same state attribute: " + key);
            }
        }
        boolean standardAction = STANDARD_ACTION_TYPES.contains(action.type());
        for (String key : changedKeys) {
            if (key == null || key.isBlank()) {
                throw new AgentCoreException(AgentErrorCode.EXTENSION_CONTRACT_VIOLATION,
                        "action result state attribute key must not be blank");
            }
            if (!standardAction && isReservedActionAttribute(key)) {
                throw new AgentCoreException(AgentErrorCode.EXTENSION_CONTRACT_VIOLATION,
                        "custom action handler changed a reserved state attribute: " + key);
            }
        }
    }

    private boolean isReservedActionAttribute(String key) {
        return key.startsWith("agent.") || key.startsWith("react.");
    }

    private void notifyListeners(AgentLifecycleContext context,
                                 Consumer<AgentLifecycleListener> notification) {
        for (AgentLifecycleListener listener : listeners) {
            try {
                notification.accept(listener);
            } catch (RuntimeException error) {
                notifyListenerFailure(context, listener, error);
                if (listener.critical()) {
                    throw new AgentCoreException(AgentErrorCode.LISTENER_FAILED,
                            "critical lifecycle listener failed: "
                                    + listener.getClass().getName() + ": "
                                    + errorMessage(error), error);
                }
            }
        }
    }

    private void notifyErrors(AgentRequest request, AgentRunState state,
                              RuntimeException error, LifecycleEvents events) {
        AgentLifecycleContext failed = events.next(
                AgentLifecycleEventType.RUN_FAILED, state,
                AgentLifecycleContext.NO_ACTION, clock.instant(), Duration.ZERO);
        notifyTerminalListeners(failed,
                listener -> listener.onError(failed, request, state, error));
    }

    private void notifyStops(AgentRequest request, AgentRunState state,
                             AgentStopDecision decision, LifecycleEvents events) {
        AgentLifecycleContext stopped = events.next(
                AgentLifecycleEventType.RUN_STOPPED, state,
                AgentLifecycleContext.NO_ACTION, clock.instant(), Duration.ZERO);
        notifyTerminalListeners(stopped,
                listener -> listener.onStop(stopped, request, state, decision));
    }

    private void notifyResult(AgentRequest request, AgentRunState state,
                              AgentResult result, LifecycleEvents events) {
        Instant returnedAt = clock.instant();
        AgentLifecycleContext returned = events.next(
                AgentLifecycleEventType.RUN_RETURNED, state,
                AgentLifecycleContext.NO_ACTION, returnedAt,
                elapsed(events.executionStartedAt(), returnedAt));
        notifyTerminalListeners(returned,
                listener -> listener.afterRun(returned, request, result));
    }

    private void notifyTerminalListeners(AgentLifecycleContext context,
                                         Consumer<AgentLifecycleListener> notification) {
        listeners.forEach(listener -> {
            try {
                notification.accept(listener);
            } catch (RuntimeException error) {
                notifyListenerFailure(context, listener, error);
            }
        });
    }

    private void notifyListenerFailure(AgentLifecycleContext context,
                                       AgentLifecycleListener failedListener,
                                       RuntimeException error) {
        for (AgentLifecycleListener listener : listeners) {
            if (listener == failedListener) {
                continue;
            }
            try {
                listener.onListenerError(context, failedListener.getClass().getName(),
                        failedListener.critical(), error);
            } catch (RuntimeException ignored) {
            }
        }
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

    private static Duration elapsed(Instant startedAt, Instant completedAt) {
        return completedAt.isBefore(startedAt)
                ? Duration.ZERO : Duration.between(startedAt, completedAt);
    }

    private static final class LifecycleEvents {
        private final AgentInvocationType invocationType;
        private final String invocationId = UUID.randomUUID().toString();
        private final String runId;
        private final Instant executionStartedAt;
        private final Instant deadline;
        private long sequence;

        private LifecycleEvents(AgentInvocationType invocationType, String runId,
                                Instant executionStartedAt, Instant deadline) {
            this.invocationType = invocationType;
            this.runId = runId;
            this.executionStartedAt = executionStartedAt;
            this.deadline = deadline;
        }

        private AgentLifecycleContext next(
                AgentLifecycleEventType eventType, AgentRunState state, int actionIndex,
                Instant occurredAt, Duration elapsed) {
            sequence++;
            return new AgentLifecycleContext(
                    AgentLifecycleContext.CURRENT_VERSION, eventType, invocationType,
                    invocationId, sequence, runId, state.turn(), actionIndex,
                    occurredAt, executionStartedAt, deadline, elapsed);
        }

        private Instant executionStartedAt() {
            return executionStartedAt;
        }
    }

    private static <T> T requireConfiguration(T value, String name) {
        if (value == null) {
            throw new AgentCoreException(AgentErrorCode.INVALID_CONFIGURATION,
                    name + " must not be null");
        }
        return value;
    }
}
