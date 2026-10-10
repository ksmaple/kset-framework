package com.kset.agent.core.action;

import com.kset.agent.core.AgentCoreException;
import com.kset.agent.core.AgentErrorCode;
import com.kset.agent.core.api.AgentRunStatus;
import com.kset.agent.core.tool.AgentTool;
import com.kset.agent.core.tool.AgentToolContext;
import com.kset.agent.core.tool.AgentToolRegistry;
import com.kset.agent.core.tool.ToolExecutionResult;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Factory for handlers of the standard protocol-neutral actions. */
public final class StandardActionHandlers {

    public static final String PLAN_CREATED = "react.planCreated";
    public static final String PLAN_TASKS = "react.planTasks";
    public static final String ANSWER_CHUNKS = "agent.answerChunks";
    public static final String PENDING_ACTION = "agent.pendingAction";
    public static final String PENDING_BATCH = "agent.pendingBatch";
    public static final String RECONCILIATION_PENDING = "agent.reconciliationPending";
    public static final String APPROVED_TOOL_OPERATIONS = "agent.approvedToolOperations";

    private StandardActionHandlers() {
    }

    public static List<AgentActionHandler> create(
            AgentToolRegistry toolRegistry, Executor toolExecutor) {
        Objects.requireNonNull(toolRegistry, "toolRegistry");
        Objects.requireNonNull(toolExecutor, "toolExecutor");
        return List.of(
                new PlanHandler(),
                new ToolCallHandler(toolRegistry),
                new ToolBatchHandler(toolRegistry, toolExecutor),
                new AnswerChunkHandler(),
                new FinalAnswerHandler(),
                new ConfirmationHandler());
    }

    private static final class PlanHandler implements AgentActionHandler {
        @Override
        public String actionType() {
            return StandardActionTypes.TASK_PLAN;
        }

        @Override
        public AgentActionResult handle(AgentAction action, AgentActionContext context) {
            TaskPlanAction plan = require(action, TaskPlanAction.class);
            List<Map<String, Object>> tasks = plan.tasks().stream()
                    .map(StandardActionHandlers::planTaskState)
                    .toList();
            Map<String, Object> attributes = Map.of(PLAN_CREATED, true, PLAN_TASKS, tasks);
            AgentObservation observation = new AgentObservation(action.actionType(), true, true,
                    plan.summary(), null, null, Map.of("tasks", tasks));
            return new AgentActionResult(List.of(observation), null, null, null,
                    attributes, Set.of());
        }
    }

    private static final class ToolCallHandler implements AgentActionHandler {
        private final AgentToolRegistry toolRegistry;

        private ToolCallHandler(AgentToolRegistry toolRegistry) {
            this.toolRegistry = toolRegistry;
        }

        @Override
        public String actionType() {
            return StandardActionTypes.TOOL_CALL;
        }

        @Override
        public AgentActionResult handle(AgentAction action, AgentActionContext context) {
            ToolCallAction call = require(action, ToolCallAction.class);
            AgentTool tool = toolRegistry.find(call.toolName()).orElse(null);
            if (tool == null) {
                return observedAndClearPending(toolFailure(
                        call, AgentErrorCode.TOOL_NOT_FOUND,
                        "tool not found: " + call.toolName()));
            }
            boolean approved = isToolApproved(tool, call, context);
            if (requiresConfirmation(tool) && !approved) {
                return AgentActionResult.suspended("tool confirmation required",
                        Map.of(PENDING_ACTION, toolCallState(call)));
            }
            AgentActionResult result;
            try {
                AgentToolContext toolContext = toolContext(
                        call, context, context.request().options().toolCallTimeout(),
                        context.executionContext().deadline());
                if (toolContext.isCancellationRequested()) {
                    result = observedAndClearPending(toolFailure(
                            call, AgentErrorCode.TOOL_CANCELLED,
                            "tool execution cancelled"));
                } else if (toolContext.isDeadlineExceeded(context.clock().instant())) {
                    result = observedAndClearPending(toolFailure(
                            call, AgentErrorCode.TOOL_TIMEOUT,
                            "tool deadline reached"));
                } else {
                    AgentObservation observation = toObservation(
                            call, tool.execute(call.arguments(), toolContext));
                    result = isResultUnknown(observation)
                            ? suspendUnknownCall(call, observation)
                            : observedAndClearPending(observation);
                }
            } catch (CancellationException error) {
                result = observedAndClearPending(toolFailure(
                        call, AgentErrorCode.TOOL_CANCELLED, errorMessage(error)));
            } catch (RuntimeException error) {
                result = observedAndClearPending(toolFailure(
                        call, AgentErrorCode.TOOL_EXECUTION_FAILED, errorMessage(error)));
            }
            return rememberApprovedOperations(
                    approved ? List.of(call) : List.of(), context, result);
        }
    }

    private static final class ToolBatchHandler implements AgentActionHandler {
        private final AgentToolRegistry toolRegistry;
        private final Executor toolExecutor;

        private ToolBatchHandler(AgentToolRegistry toolRegistry, Executor toolExecutor) {
            this.toolRegistry = toolRegistry;
            this.toolExecutor = toolExecutor;
        }

        @Override
        public String actionType() {
            return StandardActionTypes.TOOL_BATCH;
        }

        @Override
        public AgentActionResult handle(AgentAction action, AgentActionContext context) {
            ToolBatchAction batch = require(action, ToolBatchAction.class);
            List<ToolCallAction> approvedCalls = new ArrayList<>();
            ToolCallAction nextPending = null;
            for (ToolCallAction call : batch.toolCalls()) {
                AgentTool tool = toolRegistry.find(call.toolName()).orElse(null);
                if (tool != null && requiresConfirmation(tool)) {
                    if (!isToolApproved(tool, call, context)) {
                        if (nextPending == null) {
                            nextPending = call;
                        }
                    } else {
                        approvedCalls.add(call);
                    }
                }
            }
            if (nextPending != null) {
                AgentActionResult suspended = AgentActionResult.suspended(
                        "tool confirmation required",
                        Map.of(PENDING_ACTION, toolCallState(nextPending),
                                PENDING_BATCH, pendingBatchState(batch)));
                return rememberApprovedOperations(approvedCalls, context, suspended);
            }

            Instant batchStartedAt = context.clock().instant();
            Duration timeout = shorter(context.request().options().toolBatchTimeout(),
                    context.executionContext().remainingFrom(batchStartedAt));
            Duration callTimeout = shorter(context.request().options().toolCallTimeout(), timeout);
            if (context.executionContext().isCancellationRequested()) {
                return rememberApprovedOperations(approvedCalls, context,
                        observedAndClearPending(AgentObservation.failure(
                                action.actionType(), AgentErrorCode.TOOL_CANCELLED,
                                "tool batch execution cancelled")));
            }
            if (timeout.isZero()) {
                return rememberApprovedOperations(approvedCalls, context,
                        observedAndClearPending(AgentObservation.failure(
                                action.actionType(), AgentErrorCode.TOOL_TIMEOUT,
                                "tool batch deadline reached")));
            }
            Instant batchDeadline = batchStartedAt.plus(timeout);

            List<CompletableFuture<AgentObservation>> futures = new ArrayList<>();
            try {
                for (ToolCallAction call : batch.toolCalls()) {
                    futures.add(CompletableFuture.supplyAsync(
                            () -> executeCall(call, context, callTimeout, batchDeadline),
                            toolExecutor));
                }
                CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                        .get(Math.max(1L, timeout.toMillis()), TimeUnit.MILLISECONDS);
                List<AgentObservation> observations = futures.stream()
                        .map(CompletableFuture::join)
                        .toList();
                if (observations.stream().anyMatch(StandardActionHandlers::isResultUnknown)) {
                    return rememberApprovedOperations(approvedCalls, context,
                            suspendForReconciliation(
                                    observations, toolBatchState(batch),
                                    "tool batch outcome requires reconciliation"));
                }
                return rememberApprovedOperations(approvedCalls, context,
                        new AgentActionResult(
                                observations, null, null, null, Map.of(),
                                Set.of(PENDING_ACTION, PENDING_BATCH)));
            } catch (Exception error) {
                futures.forEach(future -> future.cancel(true));
                if (error instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                if (!futures.isEmpty()) {
                    return rememberApprovedOperations(approvedCalls, context,
                            suspendUnknownBatch(batch, batchFailureCode(error), error));
                }
                return rememberApprovedOperations(approvedCalls, context,
                        observedAndClearPending(AgentObservation.failure(
                                action.actionType(), AgentErrorCode.TOOL_BATCH_FAILED,
                                errorMessage(error))));
            }
        }

        private AgentObservation executeCall(
                ToolCallAction call, AgentActionContext context,
                Duration timeout, Instant batchDeadline) {
            AgentTool tool = toolRegistry.find(call.toolName()).orElse(null);
            if (tool == null) {
                return toolFailure(call,
                        AgentErrorCode.TOOL_NOT_FOUND, "tool not found: " + call.toolName());
            }
            try {
                AgentToolContext toolContext = toolContext(
                        call, context, timeout, batchDeadline);
                if (toolContext.isCancellationRequested()) {
                    return toolFailure(call,
                            AgentErrorCode.TOOL_CANCELLED, "tool execution cancelled");
                }
                if (toolContext.isDeadlineExceeded(context.clock().instant())) {
                    return toolFailure(call,
                            AgentErrorCode.TOOL_TIMEOUT, "tool deadline reached");
                }
                return toObservation(call,
                        tool.execute(call.arguments(), toolContext));
            } catch (CancellationException error) {
                return toolFailure(call,
                        AgentErrorCode.TOOL_CANCELLED, errorMessage(error));
            } catch (RuntimeException error) {
                return toolFailure(call,
                        AgentErrorCode.TOOL_EXECUTION_FAILED, errorMessage(error));
            }
        }
    }

    private static final class AnswerChunkHandler implements AgentActionHandler {
        @Override
        public String actionType() {
            return StandardActionTypes.ANSWER_CHUNK;
        }

        @Override
        public AgentActionResult handle(AgentAction action, AgentActionContext context) {
            AnswerChunkAction chunk = require(action, AnswerChunkAction.class);
            List<String> chunks = new ArrayList<>(strings(context.state().attributes().get(ANSWER_CHUNKS)));
            chunks.add(chunk.text());
            return new AgentActionResult(
                    List.of(AgentObservation.success(
                            action.actionType(), chunk.text(), true)),
                    null, null, null, Map.of(ANSWER_CHUNKS, List.copyOf(chunks)), Set.of());
        }
    }

    private static final class FinalAnswerHandler implements AgentActionHandler {
        @Override
        public String actionType() {
            return StandardActionTypes.FINAL_ANSWER;
        }

        @Override
        public AgentActionResult handle(AgentAction action, AgentActionContext context) {
            FinalAnswerAction answer = require(action, FinalAnswerAction.class);
            List<String> parts = new ArrayList<>(strings(context.state().attributes().get(ANSWER_CHUNKS)));
            parts.add(answer.answer());
            return AgentActionResult.completed(
                    String.join("\n\n", parts), Set.of(PENDING_ACTION, PENDING_BATCH));
        }
    }

    private static final class ConfirmationHandler implements AgentActionHandler {
        @Override
        public String actionType() {
            return StandardActionTypes.CONFIRMATION;
        }

        @Override
        public AgentActionResult handle(AgentAction action, AgentActionContext context) {
            ConfirmationAction confirmation = require(action, ConfirmationAction.class);
            if (context.request().isConfirmationApproved(confirmation.confirmationId())) {
                requirePendingAction(
                        context, confirmationState(confirmation), confirmation.confirmationId());
                return observedAndClearPending(AgentObservation.success(
                        action.actionType(), confirmation.message(), true));
            }
            return AgentActionResult.suspended(confirmation.message(),
                    Map.of(PENDING_ACTION, confirmationState(confirmation)));
        }
    }

    private static AgentActionResult observedAndClearPending(AgentObservation observation) {
        return AgentActionResult.observed(observation, Set.of(PENDING_ACTION, PENDING_BATCH));
    }

    private static AgentActionResult suspendUnknownBatch(
            ToolBatchAction batch, AgentErrorCode causeCode, Exception error) {
        AgentObservation observation = new AgentObservation(
                batch.actionType(), false, false, null,
                AgentErrorCode.TOOL_RESULT_UNKNOWN.name(),
                "tool batch outcome is unknown: " + errorMessage(error),
                Map.of("causeCode", causeCode.name()));
        return suspendForReconciliation(
                List.of(observation), toolBatchState(batch),
                "tool batch outcome requires reconciliation");
    }

    private static AgentActionResult suspendUnknownCall(
            ToolCallAction call, AgentObservation observation) {
        return suspendForReconciliation(
                List.of(observation), toolCallState(call),
                "tool outcome requires reconciliation");
    }

    private static AgentActionResult suspendForReconciliation(
            List<AgentObservation> observations, Map<String, Object> pendingAction,
            String suspensionMessage) {
        return new AgentActionResult(
                observations, AgentRunStatus.SUSPENDED, null, suspensionMessage,
                Map.of(PENDING_ACTION, pendingAction, RECONCILIATION_PENDING, true),
                Set.of(PENDING_BATCH));
    }

    private static boolean isResultUnknown(AgentObservation observation) {
        return AgentErrorCode.TOOL_RESULT_UNKNOWN.name().equals(observation.errorCode());
    }

    private static AgentErrorCode batchFailureCode(Exception error) {
        if (error instanceof TimeoutException) {
            return AgentErrorCode.TOOL_TIMEOUT;
        }
        if (error instanceof InterruptedException || error instanceof CancellationException) {
            return AgentErrorCode.TOOL_CANCELLED;
        }
        return AgentErrorCode.TOOL_BATCH_FAILED;
    }

    private static boolean requiresConfirmation(AgentTool tool) {
        return tool.descriptor().requiresConfirmation() || !tool.descriptor().readOnly();
    }

    private static boolean isToolApproved(
            AgentTool tool, ToolCallAction call, AgentActionContext context) {
        if (!requiresConfirmation(tool)) {
            return false;
        }
        Map<String, Object> approved = approvedToolOperations(context);
        if (call.operationDefinition().equals(approved.get(call.callId()))) {
            return true;
        }
        if (!context.request().isToolCallApproved(call.callId())) {
            return false;
        }
        requirePendingAction(context, toolCallState(call), call.callId());
        return true;
    }

    private static AgentActionResult rememberApprovedOperations(
            List<ToolCallAction> toolCalls, AgentActionContext context,
            AgentActionResult result) {
        if (toolCalls.isEmpty()) {
            return result;
        }
        Map<String, Object> approved = approvedToolOperations(context);
        toolCalls.forEach(toolCall ->
                approved.put(toolCall.callId(), toolCall.operationDefinition()));
        Map<String, Object> attributes = new LinkedHashMap<>(result.stateAttributes());
        attributes.put(APPROVED_TOOL_OPERATIONS, Map.copyOf(approved));
        return new AgentActionResult(
                result.observations(), result.requestedRunStatus(), result.answer(),
                result.suspensionMessage(), attributes, result.removedStateAttributes());
    }

    private static Map<String, Object> approvedToolOperations(AgentActionContext context) {
        Object value = context.state().attributes().get(APPROVED_TOOL_OPERATIONS);
        Map<String, Object> approved = new LinkedHashMap<>();
        if (value == null) {
            return approved;
        }
        if (!(value instanceof Map<?, ?> values)) {
            throw new AgentCoreException(AgentErrorCode.INVALID_SNAPSHOT,
                    "approved tool operation state must be a map");
        }
        values.forEach((key, identity) -> approved.put(String.valueOf(key), identity));
        return approved;
    }

    private static void requirePendingAction(
            AgentActionContext context, Map<String, Object> expected, String actionId) {
        Object pending = context.state().attributes().get(PENDING_ACTION);
        if (!expected.equals(pending)) {
            throw new AgentCoreException(AgentErrorCode.PENDING_ACTION_MISMATCH,
                    "approved action does not match pending action: " + actionId);
        }
    }

    private static AgentToolContext toolContext(
            ToolCallAction call, AgentActionContext context,
            Duration timeout, Instant maximumDeadline) {
        Instant startedAt = context.clock().instant();
        Instant deadline = earliest(
                startedAt.plus(timeout), maximumDeadline,
                context.executionContext().deadline());
        return new AgentToolContext(context.executionContext(), call.callId(), call.taskId(),
                call.toolName(), deadline);
    }

    private static Instant earliest(Instant first, Instant second, Instant third) {
        Instant earliest = first.isBefore(second) ? first : second;
        return earliest.isBefore(third) ? earliest : third;
    }

    private static Duration shorter(Duration left, Duration right) {
        return left.compareTo(right) <= 0 ? left : right;
    }

    private static AgentObservation toObservation(
            ToolCallAction call, ToolExecutionResult result) {
        if (result == null) {
            return toolFailure(
                    call, AgentErrorCode.TOOL_RESULT_MISSING, "tool returned null");
        }
        return AgentObservation.toolResult(
                call.callId(), call.taskId(), call.toolName(), result);
    }

    private static AgentObservation toolFailure(
            ToolCallAction call, AgentErrorCode errorCode, String errorMessage) {
        return AgentObservation.toolResult(
                call.callId(), call.taskId(), call.toolName(),
                ToolExecutionResult.failure(errorCode, errorMessage));
    }

    private static List<String> strings(Object value) {
        if (!(value instanceof Collection<?> values)) {
            return List.of();
        }
        return values.stream().map(String::valueOf).toList();
    }

    private static Map<String, Object> planTaskState(PlanTask task) {
        return Map.of(
                "taskId", task.taskId(),
                "title", task.title(),
                "dependsOn", task.dependsOn());
    }

    private static Map<String, Object> toolCallState(ToolCallAction call) {
        Map<String, Object> state = new LinkedHashMap<>(call.operationDefinition());
        state.put("type", call.actionType());
        return Map.copyOf(state);
    }

    private static Map<String, Object> toolBatchState(ToolBatchAction batch) {
        return Map.of(
                "type", batch.actionType(),
                "summary", batch.summary(),
                "toolCalls", batch.toolCalls().stream()
                        .map(StandardActionHandlers::toolCallState)
                        .toList());
    }

    private static List<Map<String, Object>> pendingBatchState(ToolBatchAction batch) {
        return batch.toolCalls().stream().map(ToolCallAction::operationDefinition).toList();
    }

    private static Map<String, Object> confirmationState(ConfirmationAction confirmation) {
        return Map.of(
                "type", confirmation.actionType(),
                "confirmationId", confirmation.confirmationId(),
                "message", confirmation.message(),
                "options", confirmation.options());
    }

    private static <T> T require(AgentAction action, Class<T> actionClass) {
        if (!actionClass.isInstance(action)) {
            throw new AgentCoreException(AgentErrorCode.EXTENSION_CONTRACT_VIOLATION,
                    "action class mismatch: expected " + actionClass.getSimpleName());
        }
        return actionClass.cast(action);
    }

    private static String errorMessage(Throwable error) {
        return error.getMessage() == null || error.getMessage().isBlank()
                ? error.getClass().getSimpleName() : error.getMessage();
    }
}
