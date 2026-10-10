package com.kset.agent.core.loop;

import com.kset.agent.core.AgentCoreException;
import com.kset.agent.core.AgentErrorCode;
import com.kset.agent.core.action.AgentAction;
import com.kset.agent.core.action.AgentActionContext;
import com.kset.agent.core.action.AgentActionRegistry;
import com.kset.agent.core.action.AgentActionResult;
import com.kset.agent.core.action.StandardActionHandlers;
import com.kset.agent.core.action.StandardActionTypes;
import com.kset.agent.core.action.ToolBatchAction;
import com.kset.agent.core.action.ToolCallAction;
import com.kset.agent.core.api.AgentFailure;
import com.kset.agent.core.api.AgentRequest;
import com.kset.agent.core.api.AgentResumeInput;
import com.kset.agent.core.api.AgentResult;
import com.kset.agent.core.api.AgentRunStatus;
import com.kset.agent.core.checkpoint.AgentCheckpointPort;
import com.kset.agent.core.event.AgentInvocationType;
import com.kset.agent.core.event.AgentLifecycleContext;
import com.kset.agent.core.event.AgentLifecycleEventType;
import com.kset.agent.core.event.AgentLifecycleListener;
import com.kset.agent.core.event.AgentLifecycleStepType;
import com.kset.agent.core.execution.AgentExecutionContext;
import com.kset.agent.core.id.AgentIdGenerator;
import com.kset.agent.core.model.AgentModel;
import com.kset.agent.core.model.AgentModelRetryOptions;
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
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
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
    private final AgentModelRetryOptions modelRetryOptions;
    private final AgentReasoningStrategy reasoningStrategy;
    private final String strategyAttributePrefix;
    private final AgentProtocolRegistry protocolRegistry;
    private final AgentActionRegistry actionRegistry;
    private final AgentStopController stopController;
    private final AgentCheckpointPort checkpointPort;
    private final List<AgentLifecycleListener> lifecycleListeners;
    private final Clock clock;
    private final AgentIdGenerator runIdGenerator;

    public static AgentKernelBuilder builder(AgentModel model) {
        return new AgentKernelBuilder(model);
    }

    /** Returns the immutable protocol identities available to new runs. */
    public List<AgentProtocolId> supportedProtocolIds() {
        return protocolRegistry.supportedProtocolIds();
    }

    AgentLoopKernel(AgentModel model,
                    AgentModelRetryOptions modelRetryOptions,
                    AgentReasoningStrategy reasoningStrategy,
                    String strategyId,
                    AgentProtocolRegistry protocolRegistry,
                    AgentActionRegistry actionRegistry,
                    AgentStopController stopController,
                    AgentCheckpointPort checkpointPort,
                    List<AgentLifecycleListener> lifecycleListeners,
                    Clock clock,
                    AgentIdGenerator runIdGenerator) {
        this.model = requireConfiguration(model, "model");
        this.modelRetryOptions = requireConfiguration(modelRetryOptions, "modelRetryOptions");
        this.reasoningStrategy = requireConfiguration(
                reasoningStrategy, "reasoningStrategy");
        this.strategyAttributePrefix = requireConfiguration(strategyId, "strategyId") + ".";
        this.protocolRegistry = requireConfiguration(protocolRegistry, "protocolRegistry");
        this.actionRegistry = requireConfiguration(actionRegistry, "actionRegistry");
        this.stopController = requireConfiguration(stopController, "stopController");
        this.checkpointPort = checkpointPort == null
                ? AgentCheckpointPort.noop() : checkpointPort;
        try {
            this.lifecycleListeners = lifecycleListeners == null
                    ? List.of() : List.copyOf(lifecycleListeners);
        } catch (RuntimeException error) {
            throw new AgentCoreException(AgentErrorCode.INVALID_CONFIGURATION,
                    "lifecycleListeners must not contain null", error);
        }
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.runIdGenerator = runIdGenerator;
    }

    public AgentResult run(AgentRequest request) {
        if (request == null) {
            throw new AgentCoreException(AgentErrorCode.INVALID_REQUEST,
                    "request must not be null");
        }
        AgentRequest boundRequest = bindRunId(request);
        protocolRegistry.require(boundRequest.protocolId());
        return execute(boundRequest, AgentRunState.start(boundRequest, clock.instant()),
                AgentInvocationType.RUN);
    }

    public AgentResult resume(AgentRequest request, AgentRunSnapshot snapshot) {
        return resume(request, snapshot, AgentResumeInput.none());
    }

    public AgentResult resume(
            AgentRequest request, AgentRunSnapshot snapshot, AgentResumeInput resumeInput) {
        if (request == null) {
            throw new AgentCoreException(AgentErrorCode.INVALID_REQUEST,
                    "request must not be null");
        }
        if (snapshot == null) {
            throw invalidSnapshot("snapshot must not be null");
        }
        if (resumeInput == null) {
            throw new AgentCoreException(AgentErrorCode.INVALID_RESUME_INPUT,
                    "resumeInput must not be null");
        }
        if (!request.hasRunId()) {
            throw new AgentCoreException(AgentErrorCode.INVALID_REQUEST,
                    "resume request must provide the snapshot runId");
        }
        if (!request.runId().equals(snapshot.runId())) {
            throw invalidSnapshot("request and snapshot runId do not match");
        }
        if (!request.protocolId().equals(snapshot.protocolId())) {
            throw invalidSnapshot("protocol cannot change while resuming a run");
        }
        if (!request.task().equals(snapshot.task())) {
            throw invalidSnapshot("task cannot change while resuming a run");
        }
        if (snapshot.runStatus() == AgentRunStatus.COMPLETED
                || snapshot.runStatus() == AgentRunStatus.CANCELLED) {
            throw invalidSnapshot("completed or cancelled run cannot be resumed");
        }
        protocolRegistry.require(request.protocolId());
        AgentRunState restored = AgentRunState.restore(snapshot, clock.instant());
        AgentRunState reconciled = AgentResumeReconciler.apply(
                snapshot, restored, resumeInput, clock.instant());
        return execute(request, reconciled, AgentInvocationType.RESUME);
    }

    private AgentRequest bindRunId(AgentRequest request) {
        if (request.hasRunId()) {
            return request;
        }
        if (runIdGenerator == null) {
            throw new AgentCoreException(AgentErrorCode.INVALID_CONFIGURATION,
                    "new run requires an explicit runId or a configured runIdGenerator");
        }
        String runId;
        try {
            runId = runIdGenerator.nextId();
        } catch (RuntimeException error) {
            throw new AgentCoreException(AgentErrorCode.ID_GENERATION_FAILED,
                    "runId generation failed: " + errorMessage(error), error);
        }
        if (runId == null || runId.isBlank()) {
            throw new AgentCoreException(AgentErrorCode.ID_GENERATION_FAILED,
                    "runId generator returned a blank value");
        }
        return request.withRunId(runId);
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
                AgentStopDecision beforeTurn = stopController.evaluate(
                        stopContext(request, state, executionStartedAt, deadline, events));
                if (beforeTurn.shouldStop()) {
                    return finish(request, state, beforeTurn, events);
                }

                state = state.nextTurn(clock.instant());
                AgentRunState turnState = state;
                Instant turnStartedAt = clock.instant();
                LifecycleEvents.LifecycleStep turnStep = events.beginStep(
                        AgentLifecycleStepType.TURN, "turn", turnStartedAt);
                AgentLifecycleContext turnStarted = events.next(
                        AgentLifecycleEventType.TURN_STARTED, state,
                        AgentLifecycleContext.NO_ACTION, turnStartedAt, Duration.ZERO);
                notifyListeners(turnStarted,
                        listener -> listener.beforeTurn(turnStarted, request, turnState));

                AgentTurn turn = nextTurn(request, state);
                if (!turn.protocolId().equals(state.protocolId())) {
                    throw new AgentCoreException(AgentErrorCode.EXTENSION_CONTRACT_VIOLATION,
                            "reasoning strategy changed the run protocol");
                }
                AgentProtocolCodec codec = protocolRegistry.require(turn.protocolId());
                AgentProtocolContext protocolContext = new AgentProtocolContext(request, state);
                ModelRequest modelRequest = prepareModelRequest(
                        codec, turn.modelRequest(), protocolContext);
                AgentStopDecision beforeModel = stopController.evaluateImmediate(
                        stopContext(request, state, executionStartedAt, deadline, events));
                if (beforeModel.shouldStop()) {
                    return finish(request, state, beforeModel, events);
                }
                Instant modelStartedAt = clock.instant();
                LifecycleEvents.LifecycleStep modelStep = events.beginStep(
                        AgentLifecycleStepType.MODEL, "model", modelStartedAt);
                AgentLifecycleContext modelStarted = events.next(
                        AgentLifecycleEventType.MODEL_STARTED, state,
                        AgentLifecycleContext.NO_ACTION, modelStartedAt, Duration.ZERO);
                notifyListeners(modelStarted, listener -> listener.beforeModel(
                        modelStarted, request, turnState, modelRequest));
                AgentStopDecision afterBeforeModel = stopController.evaluateImmediate(
                        stopContext(request, state, executionStartedAt, deadline, events));
                if (afterBeforeModel.shouldStop()) {
                    return finish(request, state, afterBeforeModel, events);
                }
                Instant modelExecutionStartedAt = clock.instant();
                ModelCallOutcome modelCall = generateModelWithRetry(
                        modelRequest, request, state, executionStartedAt, deadline, events);
                if (modelCall.stopDecision().shouldStop()) {
                    return finish(request, state, modelCall.stopDecision(), events);
                }
                ModelResponse response = modelCall.response();

                Instant modelCompletedAt = clock.instant();
                AgentRunState modelState = state;
                AgentLifecycleContext modelCompleted = events.next(
                        AgentLifecycleEventType.MODEL_COMPLETED, state,
                        AgentLifecycleContext.NO_ACTION, modelCompletedAt,
                        elapsed(modelExecutionStartedAt, modelCompletedAt));
                try {
                    notifyListeners(modelCompleted, listener -> listener.afterModel(
                            modelCompleted, request, modelState, response));
                } finally {
                    events.completeStep(modelStep);
                }

                AgentStopDecision afterModelExecution = stopController.evaluateImmediate(
                        stopContext(request, state, executionStartedAt, deadline, events));
                if (afterModelExecution.shouldStop()) {
                    return finish(request, state, afterModelExecution, events);
                }

                AgentDecision decision;
                Instant decisionStartedAt = clock.instant();
                LifecycleEvents.LifecycleStep decisionStep = events.beginStep(
                        AgentLifecycleStepType.DECISION, "decision", decisionStartedAt);
                AgentRunState beforeDecisionState = state;
                AgentLifecycleContext decisionStarted = events.next(
                        AgentLifecycleEventType.DECISION_STARTED, state,
                        AgentLifecycleContext.NO_ACTION, decisionStartedAt, Duration.ZERO);
                notifyListeners(decisionStarted, listener -> listener.beforeDecision(
                        decisionStarted, request, beforeDecisionState, response));
                try {
                    decision = decode(codec, response, protocolContext);
                    validateToolCallIds(state, decision);
                    AgentStopDecision capacityStop = capacityStop(request, decision);
                    if (capacityStop.shouldStop()) {
                        return finish(request, state, capacityStop, events);
                    }
                    validateDecision(request, state, decision);
                } catch (AgentProtocolException protocolError) {
                    state = state.protocolFailed(
                            protocolError.protocolErrorCode(), protocolError.getMessage(),
                            clock.instant());
                    AgentRunState failedState = state;
                    AgentLifecycleContext protocolFailed = events.next(
                            AgentLifecycleEventType.PROTOCOL_ERROR, state,
                            AgentLifecycleContext.NO_ACTION, clock.instant(), Duration.ZERO);
                    try {
                        notifyListeners(protocolFailed, listener -> listener.onProtocolError(
                                protocolFailed, request, failedState, protocolError));
                    } finally {
                        events.completeStep(decisionStep);
                    }
                    Instant turnFailedAt = clock.instant();
                    AgentLifecycleContext turnFailed = events.next(
                            AgentLifecycleEventType.TURN_FAILED, state,
                            AgentLifecycleContext.NO_ACTION, turnFailedAt,
                            elapsed(turnStartedAt, turnFailedAt));
                    try {
                        notifyListeners(turnFailed, listener -> listener.onTurnError(
                                turnFailed, request, failedState, protocolError));
                    } finally {
                        events.completeStep(turnStep);
                    }
                    AgentStopDecision protocolStop = stopController.evaluate(
                            stopContext(request, state, executionStartedAt, deadline, events));
                    if (protocolStop.shouldStop()) {
                        return finish(request, state, protocolStop, events);
                    }
                    saveCheckpoint(request, state, events);
                    continue;
                }

                AgentStopDecision afterDecision = stopController.evaluateImmediate(
                        stopContext(request, state, executionStartedAt, deadline, events));
                if (afterDecision.shouldStop()) {
                    return finish(request, state, afterDecision, events);
                }

                state = state.decisionAccepted(decision, clock.instant());
                AgentRunState decisionState = state;
                AgentLifecycleContext decisionAccepted = events.next(
                        AgentLifecycleEventType.DECISION_ACCEPTED, state,
                        AgentLifecycleContext.NO_ACTION, clock.instant(), Duration.ZERO);
                try {
                    notifyListeners(decisionAccepted, listener -> listener.afterDecision(
                            decisionAccepted, request, decisionState, decision));
                } finally {
                    events.completeStep(decisionStep);
                }

                List<AgentActionResult> results = new ArrayList<>();
                for (int actionIndex = 0; actionIndex < decision.actions().size(); actionIndex++) {
                    AgentAction action = decision.actions().get(actionIndex);
                    AgentStopDecision beforeAction = stopController.evaluateImmediate(
                            stopContext(request, state, executionStartedAt, deadline, events));
                    if (beforeAction.shouldStop()) {
                        return finish(request, state, beforeAction, events);
                    }
                    Instant actionStartedAt = clock.instant();
                    int currentActionIndex = actionIndex;
                    LifecycleEvents.LifecycleStep actionStep = events.beginStep(
                            AgentLifecycleStepType.ACTION, action.actionType(),
                            actionStartedAt, currentActionIndex);
                    AgentRunState beforeActionState = state;
                    AgentLifecycleContext actionStarted = events.next(
                            AgentLifecycleEventType.ACTION_STARTED, state, currentActionIndex,
                            actionStartedAt, Duration.ZERO);
                    notifyListeners(actionStarted, listener -> listener.beforeAction(
                            actionStarted, request, beforeActionState, action));
                    AgentStopDecision afterBeforeAction = stopController.evaluateImmediate(
                            stopContext(request, state, executionStartedAt, deadline, events));
                    if (afterBeforeAction.shouldStop()) {
                        return finish(request, state, afterBeforeAction, events);
                    }
                    Instant actionExecutionStartedAt = clock.instant();
                    AgentActionResult result = dispatchAction(action, new AgentActionContext(
                            request, state,
                            executionContext(request, state, executionStartedAt, deadline, events),
                            clock));

                    results.add(result);
                    state = state.apply(result, clock.instant());
                    AgentRunState actionState = state;
                    Instant actionCompletedAt = clock.instant();
                    AgentLifecycleContext actionCompleted = events.next(
                            AgentLifecycleEventType.ACTION_COMPLETED, state, currentActionIndex,
                            actionCompletedAt, elapsed(actionExecutionStartedAt, actionCompletedAt));
                    try {
                        notifyListeners(actionCompleted, listener -> listener.afterAction(
                                actionCompleted, request, actionState, action, result));
                    } finally {
                        events.completeStep(actionStep);
                    }

                    AgentStopDecision reconciliationStop = reconciliationStop(action, result);
                    if (reconciliationStop.shouldStop()) {
                        completeTurn(request, state, decision, results, events, turnStep,
                                turnStartedAt);
                        return finish(request, state, reconciliationStop, events);
                    }
                    AgentStopDecision afterActionExecution = stopController.evaluateImmediate(
                            stopContext(request, state, executionStartedAt, deadline, events));
                    if (afterActionExecution.shouldStop()) {
                        return finish(request, state, afterActionExecution, events);
                    }
                    if (result.terminalRunStatus() != null) {
                        completeTurn(request, state, decision, results, events, turnStep,
                                turnStartedAt);
                        AgentStopDecision terminalStop = stopController.evaluate(
                                stopContext(request, state, executionStartedAt, deadline, events));
                        if (!terminalStop.shouldStop()) {
                            throw new AgentCoreException(
                                    AgentErrorCode.EXTENSION_CONTRACT_VIOLATION,
                                    "terminal action result did not produce a stop decision");
                        }
                        return finish(request, state, terminalStop, events);
                    }

                    saveCheckpoint(request, state, events);
                    AgentStopDecision afterAction = stopController.evaluateAfterAction(
                            stopContext(request, state, executionStartedAt, deadline, events));
                    if (afterAction.shouldStop()) {
                        return finish(request, state, afterAction, events);
                    }
                }

                AgentRunState nextState = afterTurn(
                        request, state, decision, List.copyOf(results), clock.instant());
                validateStrategyState(state, nextState);
                state = nextState;
                completeTurn(request, state, decision, results, events, turnStep,
                        turnStartedAt);
                AgentStopDecision afterTurn = stopController.evaluate(
                        stopContext(request, state, executionStartedAt, deadline, events));
                if (afterTurn.shouldStop()) {
                    return finish(request, state, afterTurn, events);
                }
                saveCheckpoint(request, state, events);
            }
        } catch (RuntimeException error) {
            return fail(request, state, error, events);
        }
    }

    private void completeTurn(
            AgentRequest request, AgentRunState state, AgentDecision decision,
            List<AgentActionResult> results, LifecycleEvents events,
            LifecycleEvents.LifecycleStep turnStep, Instant turnStartedAt) {
        AgentRunState completedState = state;
        List<AgentActionResult> completedResults = List.copyOf(results);
        Instant completedAt = clock.instant();
        AgentLifecycleContext completed = events.next(
                AgentLifecycleEventType.TURN_COMPLETED, state,
                AgentLifecycleContext.NO_ACTION, completedAt,
                elapsed(turnStartedAt, completedAt));
        try {
            Consumer<AgentLifecycleListener> notification = listener -> listener.afterTurn(
                    completed, request, completedState, decision, completedResults);
            if (state.runStatus() == AgentRunStatus.RUNNING) {
                notifyListeners(completed, notification);
            } else {
                notifyTerminalListeners(completed, notification);
            }
        } finally {
            events.completeStep(turnStep);
        }
    }

    private AgentResult finish(AgentRequest request, AgentRunState state,
                               AgentStopDecision decision, LifecycleEvents events) {
        closeActiveSteps(state, events);
        AgentRunState stopped = state.stopped(decision, clock.instant());
        saveCheckpoint(request, stopped, events);
        notifyStops(request, stopped, decision, events);
        AgentResult result = result(stopped, null);
        notifyResult(request, stopped, result, events);
        return result;
    }

    private AgentResult fail(AgentRequest request, AgentRunState state,
                             RuntimeException error, LifecycleEvents events) {
        closeActiveSteps(state, events);
        AgentStopDecision decision = stopController.fatal(error);
        AgentRunState failed = state.stopped(decision, clock.instant());
        AgentCoreException checkpointError = saveFailureCheckpoint(request, failed, events);
        RuntimeException reportedError = checkpointError == null ? error : checkpointError;
        if (checkpointError != null) {
            if (checkpointError != error) {
                checkpointError.addSuppressed(error);
            }
            decision = stopController.fatal(checkpointError);
            failed = state.stopped(decision, clock.instant());
        }
        notifyErrors(request, failed, reportedError, events);
        notifyStops(request, failed, decision, events);
        AgentResult result = result(failed, failure(reportedError));
        notifyResult(request, failed, result, events);
        return result;
    }

    private void closeActiveSteps(AgentRunState state, LifecycleEvents events) {
        while (events.activeStep().stepType() != AgentLifecycleStepType.RUN) {
            LifecycleEvents.LifecycleStep step = events.activeStep();
            AgentLifecycleEventType failureType = switch (step.stepType()) {
                case TURN -> AgentLifecycleEventType.TURN_FAILED;
                case MODEL -> AgentLifecycleEventType.MODEL_FAILED;
                case DECISION -> AgentLifecycleEventType.DECISION_FAILED;
                case ACTION -> AgentLifecycleEventType.ACTION_FAILED;
                case CHECKPOINT -> AgentLifecycleEventType.CHECKPOINT_FAILED;
                case RUN -> throw new IllegalStateException("run step cannot be closed here");
            };
            Instant failedAt = clock.instant();
            AgentLifecycleContext failed = events.next(
                    failureType, state, step.actionIndex(),
                    failedAt, elapsed(step.startedAt(), failedAt));
            notifyTerminalListeners(failed, listener -> { });
            events.completeStep(step);
        }
    }

    private AgentResult result(AgentRunState state, AgentFailure failure) {
        return new AgentResult(
                state.runId(), state.runStatus(), state.answer(), state.stopDecision(),
                state.snapshot(), failure);
    }

    private AgentCoreException invalidSnapshot(String errorMessage) {
        return new AgentCoreException(AgentErrorCode.INVALID_SNAPSHOT, errorMessage);
    }

    private void saveCheckpoint(AgentRequest request, AgentRunState state, LifecycleEvents events) {
        Instant checkpointRequestedAt = clock.instant();
        LifecycleEvents.LifecycleStep checkpointStep = events.beginStep(
                AgentLifecycleStepType.CHECKPOINT, "checkpoint", checkpointRequestedAt);
        AgentRunSnapshot snapshot = state.snapshot();
        AgentLifecycleContext saving = events.next(
                AgentLifecycleEventType.CHECKPOINT_SAVING, state,
                AgentLifecycleContext.NO_ACTION, checkpointRequestedAt, Duration.ZERO);
        try {
            try {
                notifyListeners(saving, listener -> listener.beforeCheckpoint(
                        saving, request, snapshot));
            } catch (RuntimeException error) {
                notifyCheckpointError(request, state, snapshot, error,
                        checkpointRequestedAt, events);
                throw error;
            }
            Instant checkpointExecutionStartedAt = clock.instant();
            try {
                checkpointPort.save(request, snapshot);
            } catch (RuntimeException error) {
                AgentCoreException failure = checkpointFailure(error);
                notifyCheckpointError(request, state, snapshot, failure,
                        checkpointExecutionStartedAt, events);
                throw failure;
            }
            Instant checkpointCompletedAt = clock.instant();
            AgentLifecycleContext saved = events.next(
                    AgentLifecycleEventType.CHECKPOINT_SAVED, state,
                    AgentLifecycleContext.NO_ACTION, checkpointCompletedAt,
                    elapsed(checkpointExecutionStartedAt, checkpointCompletedAt));
            notifyListeners(saved, listener -> listener.afterCheckpoint(saved, request, snapshot));
        } finally {
            events.completeStep(checkpointStep);
        }
    }

    private AgentCoreException saveFailureCheckpoint(
            AgentRequest request, AgentRunState state, LifecycleEvents events) {
        Instant checkpointRequestedAt = clock.instant();
        LifecycleEvents.LifecycleStep checkpointStep = events.beginStep(
                AgentLifecycleStepType.CHECKPOINT, "checkpoint", checkpointRequestedAt);
        AgentRunSnapshot snapshot = state.snapshot();
        AgentLifecycleContext saving = events.next(
                AgentLifecycleEventType.CHECKPOINT_SAVING, state,
                AgentLifecycleContext.NO_ACTION, checkpointRequestedAt, Duration.ZERO);
        notifyTerminalListeners(saving,
                listener -> listener.beforeCheckpoint(saving, request, snapshot));
        Instant checkpointExecutionStartedAt = clock.instant();
        try {
            checkpointPort.save(request, snapshot);
            Instant checkpointCompletedAt = clock.instant();
            AgentLifecycleContext saved = events.next(
                    AgentLifecycleEventType.CHECKPOINT_SAVED, state,
                    AgentLifecycleContext.NO_ACTION, checkpointCompletedAt,
                    elapsed(checkpointExecutionStartedAt, checkpointCompletedAt));
            notifyTerminalListeners(saved,
                    listener -> listener.afterCheckpoint(saved, request, snapshot));
        } catch (RuntimeException error) {
            AgentCoreException failure = checkpointFailure(error);
            notifyCheckpointError(request, state, snapshot, failure,
                    checkpointExecutionStartedAt, events);
            return failure;
        } finally {
            events.completeStep(checkpointStep);
        }
        return null;
    }

    private AgentCoreException checkpointFailure(RuntimeException error) {
        if (error instanceof AgentCoreException coreError
                && coreError.errorCode() == AgentErrorCode.CHECKPOINT_FAILED) {
            return coreError;
        }
        return new AgentCoreException(AgentErrorCode.CHECKPOINT_FAILED,
                "checkpoint save failed: " + errorMessage(error), error);
    }

    private void notifyCheckpointError(
            AgentRequest request, AgentRunState state, AgentRunSnapshot snapshot,
            RuntimeException error, Instant checkpointExecutionStartedAt,
            LifecycleEvents events) {
        Instant checkpointFailedAt = clock.instant();
        AgentLifecycleContext failed = events.next(
                AgentLifecycleEventType.CHECKPOINT_FAILED, state,
                AgentLifecycleContext.NO_ACTION, checkpointFailedAt,
                elapsed(checkpointExecutionStartedAt, checkpointFailedAt));
        notifyTerminalListeners(failed,
                listener -> listener.onCheckpointError(failed, request, snapshot, error));
    }

    private AgentTurn nextTurn(AgentRequest request, AgentRunState state) {
        try {
            AgentTurn turn = reasoningStrategy.nextTurn(request, state);
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

    private ModelCallOutcome generateModelWithRetry(
            ModelRequest modelRequest, AgentRequest request, AgentRunState state,
            Instant executionStartedAt, Instant deadline, LifecycleEvents events) {
        for (int attempt = 1; attempt <= modelRetryOptions.maxAttempts(); attempt++) {
            if (attempt > 1) {
                AgentStopDecision beforeRetry = waitBeforeModelRetry(
                        attempt, request, state, executionStartedAt, deadline, events);
                if (beforeRetry.shouldStop()) {
                    return new ModelCallOutcome(null, beforeRetry);
                }
            }
            try {
                ModelResponse response = generateModel(modelRequest,
                        executionContext(request, state, executionStartedAt, deadline, events));
                return new ModelCallOutcome(response, AgentStopDecision.continueRun());
            } catch (AgentCoreException error) {
                if (error.errorCode() != AgentErrorCode.MODEL_CALL_FAILED
                        || !error.retryable()
                        || attempt == modelRetryOptions.maxAttempts()
                        || !clock.instant().isBefore(deadline)) {
                    throw error;
                }
            }
        }
        throw new AgentCoreException(AgentErrorCode.INTERNAL_ERROR,
                "model retry loop exhausted without a result");
    }

    private AgentStopDecision waitBeforeModelRetry(
            int nextAttempt, AgentRequest request, AgentRunState state,
            Instant executionStartedAt, Instant deadline, LifecycleEvents events) {
        long maxDelayMillis = modelRetryOptions.maxDelay().toMillis();
        long delayMillis = modelRetryOptions.initialDelay().toMillis();
        for (int attempt = 2; attempt < nextAttempt; attempt++) {
            delayMillis = Math.min(maxDelayMillis, delayMillis * 2L);
        }
        long jitteredDelayNanos = TimeUnit.MILLISECONDS.toNanos(
                ThreadLocalRandom.current().nextLong(1L, delayMillis + 1L));
        long waitUntilNanos = System.nanoTime() + jitteredDelayNanos;
        while (true) {
            AgentStopDecision stop = stopController.evaluateImmediate(
                    stopContext(request, state, executionStartedAt, deadline, events));
            if (stop.shouldStop()) {
                return stop;
            }
            long remainingDelayNanos = waitUntilNanos - System.nanoTime();
            if (remainingDelayNanos <= 0L) {
                return stopController.evaluateImmediate(
                        stopContext(request, state, executionStartedAt, deadline, events));
            }
            Duration remainingDeadline = Duration.between(clock.instant(), deadline);
            if (remainingDeadline.isZero() || remainingDeadline.isNegative()) {
                continue;
            }
            long pollNanos = TimeUnit.MILLISECONDS.toNanos(50L);
            long deadlineWaitNanos = remainingDeadline.compareTo(Duration.ofMillis(50L)) >= 0
                    ? pollNanos : remainingDeadline.toNanos();
            long sleepNanos = Math.min(Math.min(remainingDelayNanos, pollNanos),
                    deadlineWaitNanos);
            try {
                TimeUnit.NANOSECONDS.sleep(sleepNanos);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                return stopController.evaluateImmediate(
                        stopContext(request, state, executionStartedAt, deadline, events));
            }
        }
    }

    private record ModelCallOutcome(
            ModelResponse response, AgentStopDecision stopDecision) { }

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
            reasoningStrategy.validate(request, state, decision);
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
            AgentActionResult result = actionRegistry.dispatch(action, context);
            if (result == null) {
                throw new AgentCoreException(AgentErrorCode.EXTENSION_CONTRACT_VIOLATION,
                        "action handler returned null result");
            }
            if (result.terminalRunStatus() != null
                    && result.terminalRunStatus() != AgentRunStatus.COMPLETED
                    && result.terminalRunStatus() != AgentRunStatus.SUSPENDED) {
                throw new AgentCoreException(AgentErrorCode.EXTENSION_CONTRACT_VIOLATION,
                        "action handler terminal status must be COMPLETED or SUSPENDED");
            }
            if (result.terminalRunStatus() == AgentRunStatus.COMPLETED
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
            AgentRunState next = reasoningStrategy.afterTurn(
                    request, state, decision, results, now);
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
            AgentRequest request, AgentRunState state, Instant executionStartedAt,
            Instant deadline, LifecycleEvents events) {
        LifecycleEvents.LifecycleStep step = events.activeStep();
        return new AgentExecutionContext(
                request.runId(), events.invocationId(), step.stepId(), step.parentStepId(),
                step.stepType(), step.operation(), state.turn(), executionStartedAt,
                clock.instant(), deadline, request.cancellation(), request.attributes());
    }

    private AgentStopContext stopContext(
            AgentRequest request, AgentRunState state, Instant executionStartedAt,
            Instant deadline, LifecycleEvents events) {
        AgentExecutionContext executionContext = executionContext(
                request, state, executionStartedAt, deadline, events);
        return new AgentStopContext(
                request, state, executionContext, executionContext.issuedAt());
    }

    private AgentStopDecision capacityStop(AgentRequest request, AgentDecision decision) {
        if (decision.actions().size() > request.options().maxActionsPerDecision()) {
            return AgentStopDecision.stop(AgentStopReason.CAPACITY_LIMIT, AgentRunStatus.FAILED,
                    "decision action limit exceeded");
        }
        boolean oversizedBatch = decision.actions().stream()
                .filter(ToolBatchAction.class::isInstance)
                .map(ToolBatchAction.class::cast)
                .anyMatch(batch ->
                        batch.toolCalls().size() > request.options().maxToolCallsPerBatch());
        if (oversizedBatch) {
            return AgentStopDecision.stop(AgentStopReason.CAPACITY_LIMIT, AgentRunStatus.FAILED,
                    "tool batch size limit exceeded");
        }
        return AgentStopDecision.continueRun();
    }

    private static AgentStopDecision reconciliationStop(
            AgentAction action, AgentActionResult result) {
        boolean toolAction = action instanceof ToolCallAction || action instanceof ToolBatchAction;
        boolean required = toolAction
                && result.terminalRunStatus() == AgentRunStatus.SUSPENDED
                && result.observations().stream().anyMatch(observation ->
                        AgentErrorCode.TOOL_RESULT_UNKNOWN.name().equals(
                                observation.errorCode()));
        if (required && !(result.stateAttributes().get(
                StandardActionHandlers.PENDING_ACTION) instanceof java.util.Map<?, ?>)) {
            throw new AgentCoreException(AgentErrorCode.EXTENSION_CONTRACT_VIOLATION,
                    "tool reconciliation result must contain a pending action");
        }
        return required ? reconciliationStopDecision(result)
                : AgentStopDecision.continueRun();
    }

    private static AgentStopDecision reconciliationStopDecision(AgentActionResult result) {
        return AgentStopDecision.stop(AgentStopReason.RECONCILIATION_REQUIRED,
                AgentRunStatus.SUSPENDED, result.suspensionMessage());
    }

    private void validateToolCallIds(AgentRunState state, AgentDecision decision) {
        Set<String> callIds = new HashSet<>();
        Object stored = state.attributes().get(AgentRunState.TOOL_OPERATIONS_ATTRIBUTE);
        java.util.Map<?, ?> operations;
        if (stored == null) {
            operations = java.util.Map.of();
        } else if (stored instanceof java.util.Map<?, ?> values) {
            operations = values;
        } else {
            throw new AgentCoreException(AgentErrorCode.INVALID_SNAPSHOT,
                    "tool operation state must be a map");
        }
        for (AgentAction action : decision.actions()) {
            if (action instanceof ToolCallAction call) {
                validateToolCallId(call, callIds, operations);
            }
            if (action instanceof ToolBatchAction batch) {
                for (ToolCallAction call : batch.toolCalls()) {
                    validateToolCallId(call, callIds, operations);
                }
            }
        }
    }

    private void validateToolCallId(
            ToolCallAction call, Set<String> callIds, java.util.Map<?, ?> operations) {
        if (!callIds.add(call.callId())) {
            throw new AgentProtocolException(
                    "DUPLICATE_TOOL_CALL_ID", "tool call ids must be unique within a decision");
        }
        if (operations.containsKey(call.callId())
                && !call.operationIdentity().equals(operations.get(call.callId()))) {
            throw new AgentProtocolException(
                    AgentErrorCode.TOOL_IDEMPOTENCY_CONFLICT.name(),
                    "tool call id is already bound to a different operation: " + call.callId());
        }
    }

    private void validateStrategyState(AgentRunState current, AgentRunState next) {
        boolean coreStateChanged = !current.runId().equals(next.runId())
                || !current.task().equals(next.task())
                || !current.protocolId().equals(next.protocolId())
                || current.runStatus() != next.runStatus()
                || current.turn() != next.turn()
                || !current.startedAt().equals(next.startedAt())
                || next.updatedAt().isBefore(current.updatedAt())
                || !current.observations().equals(next.observations())
                || current.consecutiveProtocolErrors() != next.consecutiveProtocolErrors()
                || current.consecutiveNoProgress() != next.consecutiveNoProgress()
                || !Objects.equals(current.answer(), next.answer())
                || !Objects.equals(current.suspensionMessage(), next.suspensionMessage())
                || !Objects.equals(current.stopDecision(), next.stopDecision());
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
        boolean standardAction = STANDARD_ACTION_TYPES.contains(action.actionType());
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
        for (AgentLifecycleListener listener : lifecycleListeners) {
            boolean critical = listenerCritical(context, listener);
            try {
                listener.onEvent(context);
            } catch (RuntimeException error) {
                notifyListenerFailure(context, listener, critical, error);
                if (critical) {
                    throw new AgentCoreException(AgentErrorCode.LISTENER_FAILED,
                            "critical lifecycle event listener failed: "
                                    + listener.getClass().getName() + ": "
                                    + errorMessage(error), error);
                }
            }
            try {
                notification.accept(listener);
            } catch (RuntimeException error) {
                notifyListenerFailure(context, listener, critical, error);
                if (critical) {
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
        AgentLifecycleContext failed = events.nextRun(
                AgentLifecycleEventType.RUN_FAILED, state,
                AgentLifecycleContext.NO_ACTION, clock.instant(), Duration.ZERO);
        notifyTerminalListeners(failed,
                listener -> listener.onError(failed, request, state, error));
    }

    private void notifyStops(AgentRequest request, AgentRunState state,
                             AgentStopDecision decision, LifecycleEvents events) {
        AgentLifecycleContext stopped = events.nextRun(
                AgentLifecycleEventType.RUN_STOPPED, state,
                AgentLifecycleContext.NO_ACTION, clock.instant(), Duration.ZERO);
        notifyTerminalListeners(stopped,
                listener -> listener.onStop(stopped, request, state, decision));
    }

    private void notifyResult(AgentRequest request, AgentRunState state,
                              AgentResult result, LifecycleEvents events) {
        Instant returnedAt = clock.instant();
        AgentLifecycleContext returned = events.nextRun(
                AgentLifecycleEventType.RUN_RETURNED, state,
                AgentLifecycleContext.NO_ACTION, returnedAt,
                elapsed(events.executionStartedAt(), returnedAt));
        notifyTerminalListeners(returned,
                listener -> listener.afterRun(returned, request, result));
    }

    private void notifyTerminalListeners(AgentLifecycleContext context,
                                         Consumer<AgentLifecycleListener> notification) {
        lifecycleListeners.forEach(listener -> {
            boolean critical;
            try {
                critical = listener.critical();
            } catch (RuntimeException error) {
                notifyListenerFailure(context, listener, true, error);
                return;
            }
            try {
                listener.onEvent(context);
            } catch (RuntimeException error) {
                notifyListenerFailure(context, listener, critical, error);
            }
            try {
                notification.accept(listener);
            } catch (RuntimeException error) {
                notifyListenerFailure(context, listener, critical, error);
            }
        });
    }

    private boolean listenerCritical(
            AgentLifecycleContext context, AgentLifecycleListener listener) {
        try {
            return listener.critical();
        } catch (RuntimeException error) {
            notifyListenerFailure(context, listener, true, error);
            throw new AgentCoreException(AgentErrorCode.LISTENER_FAILED,
                    "lifecycle listener critical flag failed: "
                            + listener.getClass().getName() + ": "
                            + errorMessage(error), error);
        }
    }

    private void notifyListenerFailure(AgentLifecycleContext context,
                                       AgentLifecycleListener failedListener,
                                       boolean failedCritical,
                                       RuntimeException error) {
        for (AgentLifecycleListener listener : lifecycleListeners) {
            if (listener == failedListener) {
                continue;
            }
            try {
                listener.onListenerError(context, failedListener.getClass().getName(),
                        failedCritical, error);
            } catch (RuntimeException ignored) {
            }
        }
    }

    private AgentFailure failure(RuntimeException error) {
        if (error instanceof AgentCoreException coreError) {
            return new AgentFailure(
                    coreError.errorCode(), errorMessage(coreError), coreError.retryable());
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
        private final LifecycleStep runStep;
        private LifecycleStep activeStep;
        private long eventSequence;
        private long stepSequence;

        private LifecycleEvents(AgentInvocationType invocationType, String runId,
                                Instant executionStartedAt, Instant deadline) {
            this.invocationType = invocationType;
            this.runId = runId;
            this.executionStartedAt = executionStartedAt;
            this.deadline = deadline;
            this.runStep = createStep(null, AgentLifecycleStepType.RUN,
                    invocationType.name().toLowerCase(java.util.Locale.ROOT),
                    executionStartedAt, AgentLifecycleContext.NO_ACTION);
            this.activeStep = runStep;
        }

        private AgentLifecycleContext next(
                AgentLifecycleEventType eventType, AgentRunState state, int actionIndex,
                Instant occurredAt, Duration elapsed) {
            eventSequence++;
            LifecycleStep step = activeStep;
            return new AgentLifecycleContext(
                    AgentLifecycleContext.CURRENT_VERSION, eventType, invocationType,
                    invocationId, eventSequence, runId, step.stepId(), step.parentStepId(),
                    step.stepType(), step.operation(), state.turn(), actionIndex,
                    occurredAt, executionStartedAt, deadline, elapsed);
        }

        private AgentLifecycleContext nextRun(
                AgentLifecycleEventType eventType, AgentRunState state, int actionIndex,
                Instant occurredAt, Duration elapsed) {
            LifecycleStep current = activeStep;
            activeStep = runStep;
            try {
                return next(eventType, state, actionIndex, occurredAt, elapsed);
            } finally {
                activeStep = current;
            }
        }

        private LifecycleStep beginStep(
                AgentLifecycleStepType stepType, String operation, Instant startedAt) {
            return beginStep(stepType, operation, startedAt, AgentLifecycleContext.NO_ACTION);
        }

        private LifecycleStep beginStep(
                AgentLifecycleStepType stepType, String operation,
                Instant startedAt, int actionIndex) {
            LifecycleStep step = createStep(activeStep, stepType, operation,
                    startedAt, actionIndex);
            activeStep = step;
            return step;
        }

        private void completeStep(LifecycleStep step) {
            if (activeStep != step) {
                throw new AgentCoreException(AgentErrorCode.INTERNAL_ERROR,
                        "lifecycle step completion order is invalid");
            }
            activeStep = step.parent() == null ? runStep : step.parent();
        }

        private LifecycleStep createStep(
                LifecycleStep parent, AgentLifecycleStepType stepType,
                String operation, Instant startedAt, int actionIndex) {
            stepSequence++;
            return new LifecycleStep(invocationId + ":step:" + stepSequence,
                    parent, stepType, operation, startedAt, actionIndex);
        }

        private Instant executionStartedAt() {
            return executionStartedAt;
        }

        private String invocationId() {
            return invocationId;
        }

        private LifecycleStep activeStep() {
            return activeStep;
        }

        private record LifecycleStep(
                String stepId, LifecycleStep parent,
                AgentLifecycleStepType stepType, String operation,
                Instant startedAt, int actionIndex) {

            private String parentStepId() {
                return parent == null ? null : parent.stepId();
            }
        }
    }

    private static <T> T requireConfiguration(T configuration, String configurationName) {
        if (configuration == null) {
            throw new AgentCoreException(AgentErrorCode.INVALID_CONFIGURATION,
                    configurationName + " must not be null");
        }
        return configuration;
    }
}
