package com.kset.agent.core.action;

import com.kset.agent.core.AgentCoreException;
import com.kset.agent.core.AgentErrorCode;
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

    private StandardActionHandlers() {
    }

    public static List<AgentActionHandler> create(AgentToolRegistry tools, Executor executor) {
        Objects.requireNonNull(tools, "tools");
        Objects.requireNonNull(executor, "executor");
        return List.of(
                new PlanHandler(),
                new ToolCallHandler(tools),
                new ToolBatchHandler(tools, executor),
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
            AgentObservation observation = new AgentObservation(action.type(), true, true,
                    plan.summary(), null, null, Map.of("tasks", tasks));
            return new AgentActionResult(List.of(observation), null, null, attributes);
        }
    }

    private static final class ToolCallHandler implements AgentActionHandler {
        private final AgentToolRegistry tools;

        private ToolCallHandler(AgentToolRegistry tools) {
            this.tools = tools;
        }

        @Override
        public String actionType() {
            return StandardActionTypes.TOOL_CALL;
        }

        @Override
        public AgentActionResult handle(AgentAction action, AgentActionContext context) {
            ToolCallAction call = require(action, ToolCallAction.class);
            AgentTool tool = tools.find(call.toolName()).orElse(null);
            if (tool == null) {
                return AgentActionResult.observed(AgentObservation.failure(
                        action.type(), AgentErrorCode.TOOL_NOT_FOUND,
                        "tool not found: " + call.toolName()));
            }
            if (needsConfirmation(tool, call, context)) {
                return AgentActionResult.suspended("tool confirmation required",
                        Map.of(PENDING_ACTION, toolCallState(call)));
            }
            try {
                AgentToolContext toolContext = toolContext(
                        call, context, context.request().options().toolCallTimeout(),
                        context.execution().deadline());
                if (toolContext.isCancellationRequested()) {
                    return AgentActionResult.observed(AgentObservation.failure(
                            action.type(), AgentErrorCode.TOOL_CANCELLED,
                            "tool execution cancelled"));
                }
                if (toolContext.isDeadlineExceeded(context.clock().instant())) {
                    return AgentActionResult.observed(AgentObservation.failure(
                            action.type(), AgentErrorCode.TOOL_TIMEOUT,
                            "tool deadline reached"));
                }
                return AgentActionResult.observed(toObservation(
                        action.type(), tool.execute(call.arguments(), toolContext)));
            } catch (CancellationException error) {
                return AgentActionResult.observed(AgentObservation.failure(
                        action.type(), AgentErrorCode.TOOL_CANCELLED, message(error)));
            } catch (RuntimeException error) {
                return AgentActionResult.observed(AgentObservation.failure(
                        action.type(), AgentErrorCode.TOOL_EXECUTION_FAILED, message(error)));
            }
        }
    }

    private static final class ToolBatchHandler implements AgentActionHandler {
        private final AgentToolRegistry tools;
        private final Executor executor;

        private ToolBatchHandler(AgentToolRegistry tools, Executor executor) {
            this.tools = tools;
            this.executor = executor;
        }

        @Override
        public String actionType() {
            return StandardActionTypes.TOOL_BATCH;
        }

        @Override
        public AgentActionResult handle(AgentAction action, AgentActionContext context) {
            ToolBatchAction batch = require(action, ToolBatchAction.class);
            for (ToolCallAction call : batch.calls()) {
                AgentTool tool = tools.find(call.toolName()).orElse(null);
                if (tool != null && needsConfirmation(tool, call, context)) {
                    return AgentActionResult.suspended("tool confirmation required",
                            Map.of(PENDING_ACTION, toolCallState(call)));
                }
            }

            Instant batchStartedAt = context.clock().instant();
            Duration timeout = shorter(context.request().options().toolBatchTimeout(),
                    context.execution().remainingFrom(batchStartedAt));
            Duration callTimeout = shorter(context.request().options().toolCallTimeout(), timeout);
            if (context.execution().isCancellationRequested()) {
                return AgentActionResult.observed(AgentObservation.failure(
                        action.type(), AgentErrorCode.TOOL_CANCELLED,
                        "tool batch execution cancelled"));
            }
            if (timeout.isZero()) {
                return AgentActionResult.observed(AgentObservation.failure(
                        action.type(), AgentErrorCode.TOOL_TIMEOUT,
                        "tool batch deadline reached"));
            }
            Instant batchDeadline = batchStartedAt.plus(timeout);

            List<CompletableFuture<AgentObservation>> futures = new ArrayList<>();
            try {
                for (ToolCallAction call : batch.calls()) {
                    futures.add(CompletableFuture.supplyAsync(
                            () -> executeCall(call, context, callTimeout, batchDeadline), executor));
                }
                CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                        .get(Math.max(1L, timeout.toMillis()), TimeUnit.MILLISECONDS);
                return new AgentActionResult(futures.stream().map(CompletableFuture::join).toList(),
                        null, null, Map.of());
            } catch (Exception error) {
                futures.forEach(future -> future.cancel(true));
                if (error instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                AgentErrorCode code = error instanceof TimeoutException
                        ? AgentErrorCode.TOOL_TIMEOUT : AgentErrorCode.TOOL_BATCH_FAILED;
                return AgentActionResult.observed(AgentObservation.failure(
                        action.type(), code, message(error)));
            }
        }

        private AgentObservation executeCall(
                ToolCallAction call, AgentActionContext context,
                Duration timeout, Instant batchDeadline) {
            AgentTool tool = tools.find(call.toolName()).orElse(null);
            if (tool == null) {
                return AgentObservation.failure(StandardActionTypes.TOOL_CALL,
                        AgentErrorCode.TOOL_NOT_FOUND, "tool not found: " + call.toolName());
            }
            try {
                AgentToolContext toolContext = toolContext(
                        call, context, timeout, batchDeadline);
                if (toolContext.isCancellationRequested()) {
                    return AgentObservation.failure(StandardActionTypes.TOOL_CALL,
                            AgentErrorCode.TOOL_CANCELLED, "tool execution cancelled");
                }
                if (toolContext.isDeadlineExceeded(context.clock().instant())) {
                    return AgentObservation.failure(StandardActionTypes.TOOL_CALL,
                            AgentErrorCode.TOOL_TIMEOUT, "tool deadline reached");
                }
                return toObservation(StandardActionTypes.TOOL_CALL,
                        tool.execute(call.arguments(), toolContext));
            } catch (CancellationException error) {
                return AgentObservation.failure(StandardActionTypes.TOOL_CALL,
                        AgentErrorCode.TOOL_CANCELLED, message(error));
            } catch (RuntimeException error) {
                return AgentObservation.failure(StandardActionTypes.TOOL_CALL,
                        AgentErrorCode.TOOL_EXECUTION_FAILED, message(error));
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
                    List.of(AgentObservation.success(action.type(), chunk.text(), true)),
                    null, null, Map.of(ANSWER_CHUNKS, List.copyOf(chunks)));
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
            return AgentActionResult.completed(String.join("\n\n", parts));
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
            return AgentActionResult.suspended(confirmation.message(),
                    Map.of(PENDING_ACTION, confirmationState(confirmation)));
        }
    }

    private static boolean needsConfirmation(
            AgentTool tool, ToolCallAction call, AgentActionContext context) {
        return (tool.descriptor().requiresConfirmation() || !tool.descriptor().readOnly())
                && !context.request().isActionConfirmed(call.callId());
    }

    private static AgentToolContext toolContext(
            ToolCallAction call, AgentActionContext context,
            Duration timeout, Instant maximumDeadline) {
        Instant startedAt = context.clock().instant();
        Instant deadline = earliest(
                startedAt.plus(timeout), maximumDeadline, context.execution().deadline());
        return new AgentToolContext(context.execution(), call.callId(), call.taskId(),
                call.toolName(), deadline);
    }

    private static Instant earliest(Instant first, Instant second, Instant third) {
        Instant earliest = first.isBefore(second) ? first : second;
        return earliest.isBefore(third) ? earliest : third;
    }

    private static Duration shorter(Duration left, Duration right) {
        return left.compareTo(right) <= 0 ? left : right;
    }

    private static AgentObservation toObservation(String actionType, ToolExecutionResult result) {
        if (result == null) {
            return AgentObservation.failure(
                    actionType, AgentErrorCode.TOOL_RESULT_MISSING, "tool returned null");
        }
        return new AgentObservation(actionType, result.success(), result.progress(), result.output(),
                result.errorCode(), result.errorMessage(), result.attributes());
    }

    private static List<String> strings(Object value) {
        if (!(value instanceof Collection<?> values)) {
            return List.of();
        }
        return values.stream().map(String::valueOf).toList();
    }

    private static Map<String, Object> planTaskState(PlanTask task) {
        return Map.of(
                "taskId", task.id(),
                "title", task.title(),
                "dependsOn", task.dependencies());
    }

    private static Map<String, Object> toolCallState(ToolCallAction call) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("type", call.type());
        state.put("callId", call.callId());
        if (call.taskId() != null) {
            state.put("taskId", call.taskId());
        }
        state.put("toolName", call.toolName());
        state.put("arguments", call.arguments());
        return Map.copyOf(state);
    }

    private static Map<String, Object> confirmationState(ConfirmationAction confirmation) {
        return Map.of(
                "type", confirmation.type(),
                "confirmationId", confirmation.confirmationId(),
                "message", confirmation.message(),
                "options", confirmation.options());
    }

    private static <T> T require(AgentAction action, Class<T> type) {
        if (!type.isInstance(action)) {
            throw new AgentCoreException(AgentErrorCode.EXTENSION_CONTRACT_VIOLATION,
                    "action type mismatch: expected " + type.getSimpleName());
        }
        return type.cast(action);
    }

    private static String message(Throwable error) {
        return error.getMessage() == null || error.getMessage().isBlank()
                ? error.getClass().getSimpleName() : error.getMessage();
    }
}
