package com.kset.agent.core.loop;

import com.kset.agent.core.AgentErrorCode;
import com.kset.agent.core.action.AgentObservation;
import com.kset.agent.core.action.StandardActionHandlers;
import com.kset.agent.core.api.AgentResult;
import com.kset.agent.core.api.AgentRunStatus;
import com.kset.agent.core.model.ModelResponse;
import com.kset.agent.core.stop.AgentCancellation;
import com.kset.agent.core.stop.AgentStopReason;
import com.kset.agent.core.tool.AgentTool;
import com.kset.agent.core.tool.AgentToolContext;
import com.kset.agent.core.tool.AgentToolDescriptor;
import com.kset.agent.core.tool.InMemoryAgentToolRegistry;
import com.kset.agent.core.tool.ToolExecutionResult;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.kset.agent.core.loop.AgentTestSupport.assertLifecycleTrace;
import static com.kset.agent.core.loop.AgentTestSupport.envelope;
import static com.kset.agent.core.loop.AgentTestSupport.request;
import static org.assertj.core.api.Assertions.assertThat;

class AgentLoopKernelConcurrencyTest {

    @Test
    void sharesKernelAcrossConcurrentRunsWithoutStateOrEventLeakage() throws Exception {
        CountDownLatch bothModelsStarted = new CountDownLatch(2);
        Map<String, AtomicInteger> callsByRun = new ConcurrentHashMap<>();
        AgentTestSupport.RecordingListener listener = new AgentTestSupport.RecordingListener();
        AgentLoopKernel kernel = AgentLoopKernel.builder((modelRequest, context) -> {
            int call = callsByRun.computeIfAbsent(
                    context.runId(), ignored -> new AtomicInteger()).incrementAndGet();
            if (call == 1) {
                bothModelsStarted.countDown();
                await(bothModelsStarted);
                return ModelResponse.text(envelope("""
                        {"type":"task_plan","plan":"plan-%s","tasks":[
                          {"taskId":"task-%s","title":"Task %s","dependsOn":[]}
                        ]}
                        """.formatted(context.runId(), context.runId(), context.runId())));
            }
            return ModelResponse.text("answer-" + context.runId());
        }).listener(listener).build();
        ExecutorService callers = Executors.newFixedThreadPool(2);

        AgentResult first;
        AgentResult second;
        try {
            Future<AgentResult> firstFuture = callers.submit(() -> kernel.run(request("run-a")));
            Future<AgentResult> secondFuture = callers.submit(() -> kernel.run(request("run-b")));
            first = firstFuture.get(10, TimeUnit.SECONDS);
            second = secondFuture.get(10, TimeUnit.SECONDS);
        } finally {
            callers.shutdownNow();
        }

        assertThat(first.runStatus()).isEqualTo(AgentRunStatus.COMPLETED);
        assertThat(second.runStatus()).isEqualTo(AgentRunStatus.COMPLETED);
        assertThat(first.answer()).isEqualTo("answer-run-a");
        assertThat(second.answer()).isEqualTo("answer-run-b");
        assertThat(first.snapshot().runId()).isEqualTo("run-a");
        assertThat(second.snapshot().runId()).isEqualTo("run-b");
        assertThat(planTaskIds(first)).containsExactly("task-run-a");
        assertThat(planTaskIds(second)).containsExactly("task-run-b");
        assertThat(first.snapshot().observations())
                .noneMatch(observation -> String.valueOf(observation.output()).contains("run-b"));
        assertThat(second.snapshot().observations())
                .noneMatch(observation -> String.valueOf(observation.output()).contains("run-a"));

        Map<String, List<com.kset.agent.core.event.AgentLifecycleContext>> byInvocation =
                listener.byInvocation();
        assertThat(byInvocation).hasSize(2);
        assertThat(byInvocation.values())
                .allSatisfy(events -> {
                    assertThat(events).extracting(event -> event.runId()).containsOnly(
                            events.getFirst().runId());
                    assertLifecycleTrace(events, events.getFirst().runId());
                });
        assertThat(byInvocation.values().stream()
                .map(events -> events.getFirst().runId()).collect(java.util.stream.Collectors.toSet()))
                .containsExactlyInAnyOrder("run-a", "run-b");
    }

    @Test
    void keepsCancellationScopedToItsOwnRequest() throws Exception {
        AtomicBoolean cancelFirst = new AtomicBoolean(true);
        AgentCancellation firstCancellation = cancelFirst::get;
        AgentLoopKernel kernel = AgentLoopKernel.builder((modelRequest, context) ->
                ModelResponse.text("answer-" + context.runId())).build();
        ExecutorService callers = Executors.newFixedThreadPool(2);

        AgentResult cancelled;
        AgentResult completed;
        try {
            Future<AgentResult> cancelledFuture = callers.submit(() -> kernel.run(request(
                    "cancelled-run", AgentLoopOptions.defaults(), Map.of(), firstCancellation)));
            Future<AgentResult> completedFuture = callers.submit(
                    () -> kernel.run(request("independent-run")));
            cancelled = cancelledFuture.get(10, TimeUnit.SECONDS);
            completed = completedFuture.get(10, TimeUnit.SECONDS);
        } finally {
            callers.shutdownNow();
        }

        assertThat(cancelled.runStatus()).isEqualTo(AgentRunStatus.CANCELLED);
        assertThat(cancelled.stopDecision().stopReason()).isEqualTo(AgentStopReason.USER_CANCELLED);
        assertThat(completed.runStatus()).isEqualTo(AgentRunStatus.COMPLETED);
        assertThat(completed.answer()).isEqualTo("answer-independent-run");
    }

    @Test
    void executesToolBatchConcurrentlyAndSuspendsWhenOneResultIsUnknown() {
        CountDownLatch bothToolsStarted = new CountDownLatch(2);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximumActive = new AtomicInteger();
        Set<String> operationIds = ConcurrentHashMap.newKeySet();
        AgentTool tool = new AgentTool() {
            @Override
            public AgentToolDescriptor descriptor() {
                return new AgentToolDescriptor(
                        "parallel", "parallel test tool", Map.of(), true, false, Map.of());
            }

            @Override
            public ToolExecutionResult execute(
                    Map<String, Object> arguments, AgentToolContext context) {
                int current = active.incrementAndGet();
                maximumActive.accumulateAndGet(current, Math::max);
                operationIds.add(context.operationId().runId() + ":"
                        + context.operationId().callId());
                bothToolsStarted.countDown();
                try {
                    await(bothToolsStarted);
                    if (context.callId().endsWith("2")) {
                        return ToolExecutionResult.failure(
                                AgentErrorCode.TOOL_RESULT_UNKNOWN, "result unavailable");
                    }
                    return ToolExecutionResult.success(context.callId());
                } finally {
                    active.decrementAndGet();
                }
            }
        };
        AgentTestSupport.ScriptedModel model = new AgentTestSupport.ScriptedModel(
                envelope("""
                        {"type":"task_plan","plan":"parallel","tasks":[
                          {"taskId":"batch","title":"Batch","dependsOn":[]}
                        ]}
                        """),
                envelope("""
                        {"type":"tool_batch","plan":"parallel calls","toolCalls":[
                          {"callId":"call-1","taskId":"batch","toolName":"parallel","arguments":{}},
                          {"callId":"call-2","taskId":"batch","toolName":"parallel","arguments":{}}
                        ]}
                        """));
        ExecutorService toolExecutor = Executors.newFixedThreadPool(2);
        AgentLoopKernel kernel = AgentLoopKernel.builder(model)
                .tools(new InMemoryAgentToolRegistry().register(tool))
                .toolExecutor(toolExecutor)
                .build();

        AgentResult result;
        try {
            result = kernel.run(request("batch-run"));
        } finally {
            toolExecutor.shutdownNow();
        }

        assertThat(result.runStatus()).isEqualTo(AgentRunStatus.SUSPENDED);
        assertThat(result.stopDecision().stopReason()).isEqualTo(AgentStopReason.RECONCILIATION_REQUIRED);
        assertThat(maximumActive).hasValue(2);
        assertThat(operationIds).containsExactlyInAnyOrder(
                "batch-run:call-1", "batch-run:call-2");
        assertThat(result.snapshot().observations().stream()
                .filter(observation -> observation.attributes().containsKey(
                        AgentObservation.TOOL_CALL_ID)))
                .extracting(observation -> observation.attributes().get(
                        AgentObservation.TOOL_CALL_ID))
                .containsExactly("call-1", "call-2");
        assertThat(result.snapshot().attributes())
                .containsKey(StandardActionHandlers.PENDING_ACTION);
        Map<?, ?> pending = (Map<?, ?>) result.snapshot().attributes().get(
                StandardActionHandlers.PENDING_ACTION);
        assertThat(pending.get("type")).isEqualTo("tool_batch");
    }

    @Test
    void suspendsTimedOutToolBatchForReconciliation() {
        CountDownLatch releaseTools = new CountDownLatch(1);
        AgentTool tool = new AgentTool() {
            @Override
            public AgentToolDescriptor descriptor() {
                return new AgentToolDescriptor(
                        "slow", "slow test tool", Map.of(), true, false, Map.of());
            }

            @Override
            public ToolExecutionResult execute(
                    Map<String, Object> arguments, AgentToolContext context) {
                await(releaseTools);
                return ToolExecutionResult.success(context.callId());
            }
        };
        AgentTestSupport.ScriptedModel model = new AgentTestSupport.ScriptedModel(
                envelope("""
                        {"type":"task_plan","plan":"slow batch","tasks":[
                          {"taskId":"batch","title":"Batch","dependsOn":[]}
                        ]}
                        """),
                envelope("""
                        {"type":"tool_batch","plan":"slow calls","toolCalls":[
                          {"callId":"slow-1","taskId":"batch","toolName":"slow","arguments":{}},
                          {"callId":"slow-2","taskId":"batch","toolName":"slow","arguments":{}}
                        ]}
                        """));
        AgentLoopOptions options = new AgentLoopOptions(
                10, Duration.ofSeconds(10), 3, 3, 256,
                Duration.ofSeconds(3), Duration.ofMillis(100), 8, 8);
        AgentTestSupport.RecordingListener listener = new AgentTestSupport.RecordingListener();
        ExecutorService toolExecutor = Executors.newFixedThreadPool(2);
        AgentLoopKernel kernel = AgentLoopKernel.builder(model)
                .tools(new InMemoryAgentToolRegistry().register(tool))
                .toolExecutor(toolExecutor).listener(listener).build();

        AgentResult result;
        try {
            result = kernel.run(request("timed-out-batch", options, Map.of(), null));
        } finally {
            releaseTools.countDown();
            toolExecutor.shutdownNow();
        }

        assertThat(result.runStatus()).isEqualTo(AgentRunStatus.SUSPENDED);
        assertThat(result.stopDecision().stopReason())
                .isEqualTo(AgentStopReason.RECONCILIATION_REQUIRED);
        assertThat(result.snapshot().observations().getLast().errorCode())
                .isEqualTo(AgentErrorCode.TOOL_RESULT_UNKNOWN.name());
        assertThat(result.snapshot().observations().getLast().attributes())
                .containsEntry("causeCode", AgentErrorCode.TOOL_TIMEOUT.name());
        assertThat(result.snapshot().attributes())
                .containsKey(StandardActionHandlers.PENDING_ACTION);
        assertThat(model.calls()).isEqualTo(2);
        assertLifecycleTrace(listener.events(), "timed-out-batch");
    }

    @SuppressWarnings("unchecked")
    private static List<String> planTaskIds(AgentResult result) {
        List<Map<String, Object>> tasks = (List<Map<String, Object>>) result.snapshot()
                .attributes().get(StandardActionHandlers.PLAN_TASKS);
        return tasks.stream().map(task -> String.valueOf(task.get("taskId"))).toList();
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("concurrent test barrier timed out");
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("concurrent test interrupted", error);
        }
    }
}
