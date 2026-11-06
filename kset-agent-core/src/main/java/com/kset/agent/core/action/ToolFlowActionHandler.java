package com.kset.agent.core.action;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kset.agent.core.AgentErrorCode;
import com.kset.agent.core.loop.AgentRunState;
import com.kset.agent.core.tool.AgentTool;
import com.kset.agent.core.tool.AgentToolContext;
import com.kset.agent.core.tool.AgentToolRegistry;
import com.kset.agent.core.tool.ToolExecutionResult;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
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

/** Executes opt-in read-only tool graphs in dependency layers. */
public final class ToolFlowActionHandler implements AgentActionHandler {

    public static final String DEPENDENCY_FAILED = "TOOL_DEPENDENCY_FAILED";
    public static final String REFERENCE_MISSING = "TOOL_RESULT_REFERENCE_MISSING";
    public static final String READ_ONLY_REQUIRED = "TOOL_FLOW_READ_ONLY_REQUIRED";

    private final AgentToolRegistry tools;
    private final Executor executor;
    private final ObjectMapper objectMapper;

    public ToolFlowActionHandler(AgentToolRegistry tools, Executor executor,
                                 ObjectMapper objectMapper) {
        this.tools = Objects.requireNonNull(tools, "tools");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper").copy();
    }

    @Override
    public String actionType() {
        return ToolFlowPlan.ACTION_TYPE;
    }

    @Override
    public AgentActionResult handle(AgentAction action, AgentActionContext context) {
        if (!(action instanceof ExtensionAction extension)) {
            return AgentActionResult.observed(AgentObservation.failure(
                    actionType(), "INVALID_TOOL_FLOW", "tool_flow requires an extension action"));
        }
        ToolFlowPlan plan;
        try {
            plan = ToolFlowPlan.parse(extension.payload());
        } catch (IllegalArgumentException error) {
            return AgentActionResult.observed(AgentObservation.failure(
                    actionType(), "INVALID_TOOL_FLOW", error.getMessage()));
        }
        if (plan.calls().size() > context.request().options().maxToolCallsPerBatch()) {
            return failAll(plan, AgentErrorCode.TOOL_BATCH_FAILED.name(),
                    "tool_flow call limit exceeded");
        }
        if (!matchesPlan(plan, context)) {
            return failAll(plan, "INVALID_TOOL_FLOW",
                    "tool_flow task references or plan dependencies are invalid");
        }
        if (!freshCallIds(plan, context)) {
            return failAll(plan, AgentErrorCode.TOOL_IDEMPOTENCY_CONFLICT.name(),
                    "tool_flow callId was already used in this run");
        }
        for (ToolFlowPlan.Call call : plan.calls()) {
            AgentTool tool = tools.find(call.toolName()).orElse(null);
            if (tool != null && (!tool.descriptor().readOnly()
                    || tool.descriptor().requiresConfirmation())) {
                return failAll(plan, READ_ONLY_REQUIRED,
                        "tool_flow accepts only read-only tools without confirmation");
            }
        }

        Instant started = context.clock().instant();
        Duration remaining = context.executionContext().remainingFrom(started);
        Duration timeout = min(context.request().options().toolBatchTimeout(), remaining);
        Instant deadline = started.plus(timeout);
        Map<String, AgentObservation> results = new LinkedHashMap<>();
        boolean aborted = context.executionContext().isCancellationRequested() || timeout.isZero();
        String abortCode = context.executionContext().isCancellationRequested()
                ? AgentErrorCode.TOOL_CANCELLED.name() : AgentErrorCode.TOOL_TIMEOUT.name();
        for (List<ToolFlowPlan.Call> layer : plan.layers()) {
            if (aborted) {
                break;
            }
            Map<String, CompletableFuture<AgentObservation>> running = new LinkedHashMap<>();
            for (ToolFlowPlan.Call call : layer) {
                String failedDependency = call.dependsOn().stream()
                        .filter(dependency -> !results.get(dependency).success())
                        .findFirst().orElse(null);
                if (failedDependency != null) {
                    results.put(call.callId(), failure(call, DEPENDENCY_FAILED,
                            "dependency failed: " + failedDependency));
                    continue;
                }
                Map<String, Object> arguments;
                try {
                    arguments = resolveArguments(call.arguments(), call.dependsOn(), results);
                } catch (MissingReference error) {
                    results.put(call.callId(), failure(call, REFERENCE_MISSING,
                            "dependency result reference is missing"));
                    continue;
                }
                if (!context.clock().instant().isBefore(deadline)
                        || context.executionContext().isCancellationRequested()) {
                    aborted = true;
                    abortCode = context.executionContext().isCancellationRequested()
                            ? AgentErrorCode.TOOL_CANCELLED.name()
                            : AgentErrorCode.TOOL_TIMEOUT.name();
                    break;
                }
                try {
                    running.put(call.callId(), CompletableFuture.supplyAsync(
                            () -> execute(call, arguments, context, deadline), executor));
                } catch (RuntimeException error) {
                    aborted = true;
                    abortCode = AgentErrorCode.TOOL_BATCH_FAILED.name();
                    break;
                }
            }
            if (!running.isEmpty()) {
                try {
                    long waitMillis = Math.max(1L, Duration.between(
                            context.clock().instant(), deadline).toMillis());
                    CompletableFuture.allOf(running.values().toArray(CompletableFuture[]::new))
                            .get(waitMillis, TimeUnit.MILLISECONDS);
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    aborted = true;
                    abortCode = AgentErrorCode.TOOL_CANCELLED.name();
                } catch (TimeoutException error) {
                    aborted = true;
                    abortCode = AgentErrorCode.TOOL_TIMEOUT.name();
                } catch (java.util.concurrent.ExecutionException error) {
                    aborted = true;
                    abortCode = AgentErrorCode.TOOL_EXECUTION_FAILED.name();
                }
                for (ToolFlowPlan.Call call : layer) {
                    CompletableFuture<AgentObservation> future = running.get(call.callId());
                    if (future == null) {
                        continue;
                    }
                    if (future.isDone() && !future.isCompletedExceptionally()
                            && !future.isCancelled()) {
                        results.put(call.callId(), future.getNow(null));
                    } else {
                        future.cancel(false);
                        results.put(call.callId(), failure(call,
                                aborted ? abortCode
                                        : AgentErrorCode.TOOL_EXECUTION_FAILED.name(),
                                "tool_flow call did not complete"));
                    }
                }
            }
        }
        for (ToolFlowPlan.Call call : plan.calls()) {
            if (!results.containsKey(call.callId())) {
                results.put(call.callId(), failure(call,
                        aborted ? abortCode : DEPENDENCY_FAILED,
                        "tool_flow call was not executed"));
            }
        }
        return new AgentActionResult(plan.calls().stream()
                .map(call -> results.get(call.callId())).toList(),
                null, null, null, Map.of(), java.util.Set.of());
    }

    private AgentObservation execute(ToolFlowPlan.Call call, Map<String, Object> arguments,
                                     AgentActionContext context, Instant batchDeadline) {
        AgentTool tool = tools.find(call.toolName()).orElse(null);
        if (tool == null) {
            return failure(call, AgentErrorCode.TOOL_NOT_FOUND.name(), "tool not found");
        }
        Instant now = context.clock().instant();
        Instant callDeadline = now.plus(context.request().options().toolCallTimeout());
        Instant deadline = callDeadline.isBefore(batchDeadline) ? callDeadline : batchDeadline;
        AgentToolContext toolContext = new AgentToolContext(
                context.executionContext(), call.callId(), call.taskId(),
                call.toolName(), deadline);
        if (toolContext.isCancellationRequested()) {
            return failure(call, AgentErrorCode.TOOL_CANCELLED.name(), "tool call cancelled");
        }
        if (toolContext.isDeadlineExceeded(now)) {
            return failure(call, AgentErrorCode.TOOL_TIMEOUT.name(), "tool deadline reached");
        }
        try {
            ToolExecutionResult result = tool.execute(arguments, toolContext);
            return result == null
                    ? failure(call, AgentErrorCode.TOOL_RESULT_MISSING.name(),
                            "tool returned null")
                    : AgentObservation.toolResult(
                            call.callId(), call.taskId(), call.toolName(), result);
        } catch (CancellationException error) {
            return failure(call, AgentErrorCode.TOOL_CANCELLED.name(), "tool call cancelled");
        } catch (RuntimeException error) {
            return failure(call, AgentErrorCode.TOOL_EXECUTION_FAILED.name(),
                    "tool execution failed");
        }
    }

    private Map<String, Object> resolveArguments(Map<String, Object> arguments,
                                                  List<String> dependencies,
                                                  Map<String, AgentObservation> results) {
        Map<String, Object> resolved = new LinkedHashMap<>();
        arguments.forEach((key, value) ->
                resolved.put(key, resolve(value, dependencies, results)));
        return resolved;
    }

    private Object resolve(Object value, List<String> dependencies,
                           Map<String, AgentObservation> results) {
        if (value instanceof Map<?, ?> map) {
            if (map.containsKey(ToolFlowPlan.RESULT_REFERENCE)) {
                ToolFlowPlan.Reference reference = ToolFlowPlan.reference(map, dependencies);
                Object output = results.get(reference.callId()).output();
                if (output == null) {
                    throw new MissingReference();
                }
                try {
                    JsonNode selected = objectMapper.valueToTree(output)
                            .at(JsonPointer.compile(reference.path()));
                    if (selected.isMissingNode() || selected.isNull()) {
                        throw new MissingReference();
                    }
                    return objectMapper.convertValue(selected, Object.class);
                } catch (IllegalArgumentException error) {
                    throw new MissingReference();
                }
            }
            Map<String, Object> resolved = new LinkedHashMap<>();
            map.forEach((key, item) ->
                    resolved.put((String) key, resolve(item, dependencies, results)));
            return resolved;
        }
        if (value instanceof List<?> list) {
            List<Object> resolved = new ArrayList<>(list.size());
            list.forEach(item -> resolved.add(resolve(item, dependencies, results)));
            return resolved;
        }
        return value;
    }

    private static AgentActionResult failAll(ToolFlowPlan plan, String errorCode, String message) {
        return new AgentActionResult(plan.calls().stream()
                .map(call -> failure(call, errorCode, message)).toList(),
                null, null, null, Map.of(), java.util.Set.of());
    }

    private static boolean matchesPlan(ToolFlowPlan plan, AgentActionContext context) {
        if (!Boolean.TRUE.equals(context.state().attributes().get(
                StandardActionHandlers.PLAN_CREATED))) {
            return true;
        }
        Object value = context.state().attributes().get(StandardActionHandlers.PLAN_TASKS);
        if (!(value instanceof List<?> tasks)) {
            return false;
        }
        Map<String, List<String>> taskDependencies = new LinkedHashMap<>();
        for (Object task : tasks) {
            if (!(task instanceof Map<?, ?> details)
                    || !(details.get("taskId") instanceof String taskId)
                    || !(details.get("dependsOn") instanceof List<?> dependencies)
                    || dependencies.stream().anyMatch(item -> !(item instanceof String))) {
                return false;
            }
            taskDependencies.put(taskId, dependencies.stream()
                    .map(String.class::cast).toList());
        }
        Map<String, ToolFlowPlan.Call> byId = new LinkedHashMap<>();
        plan.calls().forEach(call -> byId.put(call.callId(), call));
        for (ToolFlowPlan.Call call : plan.calls()) {
            if (!taskDependencies.containsKey(call.taskId())) {
                return false;
            }
            Set<String> ancestorCalls = new java.util.HashSet<>();
            collectCallAncestors(call, byId, ancestorCalls);
            Set<String> ancestorTasks = new java.util.HashSet<>();
            collectTaskAncestors(call.taskId(), taskDependencies, ancestorTasks);
            for (ToolFlowPlan.Call other : plan.calls()) {
                if (ancestorTasks.contains(other.taskId())
                        && !ancestorCalls.contains(other.callId())) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean freshCallIds(ToolFlowPlan plan, AgentActionContext context) {
        Set<String> previous = new java.util.HashSet<>();
        Object operations = context.state().attributes().get(
                AgentRunState.TOOL_OPERATIONS_ATTRIBUTE);
        if (operations instanceof Map<?, ?> recorded) {
            recorded.keySet().forEach(key -> previous.add(String.valueOf(key)));
        }
        context.state().observations().forEach(observation -> {
            if (!StandardActionTypes.TOOL_CALL.equals(observation.actionType())) {
                return;
            }
            Object callId = observation.attributes().get(AgentObservation.TOOL_CALL_ID);
            if (callId instanceof String id) {
                previous.add(id);
            }
        });
        return plan.calls().stream().noneMatch(call -> previous.contains(call.callId()));
    }

    private static void collectCallAncestors(ToolFlowPlan.Call call,
                                             Map<String, ToolFlowPlan.Call> byId,
                                             Set<String> result) {
        for (String dependency : call.dependsOn()) {
            if (result.add(dependency)) {
                collectCallAncestors(byId.get(dependency), byId, result);
            }
        }
    }

    private static void collectTaskAncestors(String taskId,
                                             Map<String, List<String>> dependencies,
                                             Set<String> result) {
        for (String dependency : dependencies.getOrDefault(taskId, List.of())) {
            if (result.add(dependency)) {
                collectTaskAncestors(dependency, dependencies, result);
            }
        }
    }

    private static AgentObservation failure(ToolFlowPlan.Call call,
                                            String errorCode, String message) {
        return AgentObservation.toolResult(call.callId(), call.taskId(), call.toolName(),
                ToolExecutionResult.failure(errorCode, message));
    }

    private static Duration min(Duration first, Duration second) {
        return first.compareTo(second) <= 0 ? first : second;
    }

    private static final class MissingReference extends RuntimeException { }
}
