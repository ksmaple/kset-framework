package com.kset.agent.runtime;

import com.kset.agent.core.AgentErrorCode;
import com.kset.agent.core.action.AgentObservation;
import com.kset.agent.core.api.AgentRequest;
import com.kset.agent.core.api.AgentResumeInput;
import com.kset.agent.core.api.AgentResult;
import com.kset.agent.core.api.AgentRunStatus;
import com.kset.agent.core.loop.AgentLoopOptions;
import com.kset.agent.core.model.ModelCapacity;
import com.kset.agent.core.model.ModelRequest;
import com.kset.agent.core.model.ModelResponse;
import com.kset.agent.core.protocol.json.AgentJsonV1Codec;
import com.kset.agent.core.stop.AgentStopReason;
import com.kset.agent.core.tool.AgentTool;
import com.kset.agent.core.tool.AgentToolContext;
import com.kset.agent.core.tool.AgentToolDescriptor;
import com.kset.agent.core.tool.ToolExecutionResult;
import com.kset.agent.runtime.mcp.McpAgentClient;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AgentRuntimeFlowTest {

    @Test
    void fixedModelNameBuilderAppliesCatalogLimit() {
        AtomicReference<ModelRequest> observed = new AtomicReference<>();
        AgentRuntime runtime = AgentRuntime.builder("gpt-4.1", (modelRequest, context) -> {
            observed.set(modelRequest);
            return ModelResponse.text("done");
        }).build();
        AgentLoopOptions options = new AgentLoopOptions(20, Duration.ofSeconds(10),
                3, 3, 100_000, Duration.ofSeconds(3), Duration.ofSeconds(3), 8, 8);
        AgentRequest request = new AgentRequest("named-runtime", "inspect", AgentJsonV1Codec.ID,
                options, Map.of(), null);

        AgentResult result = runtime.run(request, context("user-1"));

        assertThat(result.runStatus()).isEqualTo(AgentRunStatus.COMPLETED);
        assertThat(observed.get().maxOutputTokens()).isEqualTo(32_768);
    }

    @Test
    void runtimePassesCustomCapacityToSelectedModel() {
        AtomicReference<ModelRequest> observed = new AtomicReference<>();
        AgentRuntime runtime = AgentRuntime.builder("private-model", (modelRequest, context) -> {
            observed.set(modelRequest);
            return ModelResponse.text("done");
        }).modelCapacity(new ModelCapacity("Private", "private-model", 16_000, 2_048))
                .build();
        AgentLoopOptions options = new AgentLoopOptions(20, Duration.ofSeconds(10),
                3, 3, 10_000, Duration.ofSeconds(3), Duration.ofSeconds(3), 8, 8);
        AgentRequest request = new AgentRequest("custom-runtime", "inspect", AgentJsonV1Codec.ID,
                options, Map.of(), null);

        AgentResult result = runtime.run(request, context("user-1"));

        assertThat(result.runStatus()).isEqualTo(AgentRunStatus.COMPLETED);
        assertThat(observed.get().maxOutputTokens()).isEqualTo(2_048);
    }

    @Test
    void runsSelectedSkillAndMcpToolsWithCurrentBusinessContext() {
        AtomicInteger modelCalls = new AtomicInteger();
        AtomicInteger skillCalls = new AtomicInteger();
        AtomicInteger mcpCalls = new AtomicInteger();
        List<AgentBusinessContext> modelContexts = new ArrayList<>();
        McpAgentClient mcp = new McpAgentClient() {
            @Override
            public List<AgentToolDescriptor> listTools(AgentBusinessContext context) {
                assertThat(context.principalId()).isEqualTo("user-1");
                return List.of(descriptor("mcp-read", true));
            }

            @Override
            public ToolExecutionResult call(String toolName, Map<String, Object> arguments,
                                            AgentToolContext toolContext,
                                            AgentBusinessContext businessContext) {
                mcpCalls.incrementAndGet();
                assertThat(toolName).isEqualTo("mcp-read");
                assertThat(businessContext).isSameAs(AgentRuntimeContexts.require(toolContext));
                assertThat(businessContext.roles()).contains("reader");
                return ToolExecutionResult.success("mcp-result");
            }
        };
        AgentRuntime runtime = AgentRuntime.builder((modelRequest, executionContext) -> {
            modelContexts.add(AgentRuntimeContexts.require(executionContext));
            assertThat(modelRequest.userPrompt()).doesNotContain("private-content");
            return switch (modelCalls.incrementAndGet()) {
                case 1 -> ModelResponse.text(plan());
                case 2 -> ModelResponse.text(toolCall("skill-1", "skill-read"));
                case 3 -> ModelResponse.text(toolCall("mcp-1", "mcp-read"));
                default -> ModelResponse.text("finished");
            };
        }).skill(new AgentSkill("read-skill", context -> List.of(new AgentTool() {
            @Override
            public AgentToolDescriptor descriptor() {
                return AgentRuntimeFlowTest.descriptor("skill-read", true);
            }

            @Override
            public ToolExecutionResult execute(Map<String, Object> arguments,
                                               AgentToolContext toolContext) {
                skillCalls.incrementAndGet();
                assertThat(AgentRuntimeContexts.require(toolContext).attributes())
                        .containsEntry("submittedContent", "private-content");
                return ToolExecutionResult.success("skill-result");
            }
        }))).mcpTools(mcp).build();
        AgentBusinessContext context = new AgentBusinessContext("user-1", "tenant-1",
                Set.of("reader"), Map.of("submittedContent", "private-content"));

        AgentResult result = runtime.run(request("skill-mcp-run"), context,
                List.of("read-skill"));

        assertThat(result.runStatus()).isEqualTo(AgentRunStatus.COMPLETED);
        assertThat(result.answer()).isEqualTo("finished");
        assertThat(skillCalls).hasValue(1);
        assertThat(mcpCalls).hasValue(1);
        assertThat(modelContexts).containsOnly(context);
        assertThat(result.snapshot().observations()).extracting(AgentObservation::output)
                .contains("skill-result", "mcp-result");
        assertThat(result.snapshot().attributes().keySet())
                .noneMatch(key -> key.startsWith("kset.runtime."));
    }

    @Test
    void resumesUnknownToolResultWithFreshBusinessContext() {
        AtomicInteger modelCalls = new AtomicInteger();
        List<String> modelPrincipals = new ArrayList<>();
        AgentRuntime runtime = AgentRuntime.builder((modelRequest, executionContext) -> {
            modelPrincipals.add(AgentRuntimeContexts.require(executionContext).principalId());
            return switch (modelCalls.incrementAndGet()) {
                case 1 -> ModelResponse.text(plan());
                case 2 -> ModelResponse.text(toolCall("read-1", "lookup"));
                default -> ModelResponse.text("reconciled");
            };
        }).tools(context -> List.of(new AgentTool() {
            @Override
            public AgentToolDescriptor descriptor() {
                return AgentRuntimeFlowTest.descriptor("lookup", true);
            }

            @Override
            public ToolExecutionResult execute(Map<String, Object> arguments,
                                               AgentToolContext toolContext) {
                assertThat(AgentRuntimeContexts.require(toolContext).principalId())
                        .isEqualTo("old-user");
                return ToolExecutionResult.failure(AgentErrorCode.TOOL_RESULT_UNKNOWN,
                        "result requires reconciliation");
            }
        })).build();
        AgentRequest request = request("resume-run");
        AgentBusinessContext initialContext = context("old-user");

        AgentResult suspended = runtime.run(request, initialContext);

        assertThat(suspended.runStatus()).isEqualTo(AgentRunStatus.SUSPENDED);
        assertThat(suspended.stopDecision().stopReason())
                .isEqualTo(AgentStopReason.RECONCILIATION_REQUIRED);
        assertThat(suspended.snapshot().attributes().keySet())
                .noneMatch(key -> key.startsWith("kset.runtime."));
        AgentObservation authoritative = AgentObservation.toolResult("read-1", "inspect",
                "lookup", ToolExecutionResult.success("saved"));
        AgentResult resumed = runtime.resume(request, suspended.snapshot(),
                new AgentResumeInput(List.of(authoritative)), context("new-user"), List.of());

        assertThat(resumed.runStatus()).isEqualTo(AgentRunStatus.COMPLETED);
        assertThat(resumed.answer()).isEqualTo("reconciled");
        assertThat(modelPrincipals).containsExactly("old-user", "old-user", "new-user");
        assertThat(resumed.snapshot().observations()).extracting(AgentObservation::output)
                .contains("saved");
    }

    @Test
    void sharesRuntimeAcrossConcurrentCallsWithoutContextLeakage() throws Exception {
        int count = 4;
        CountDownLatch allToolsStarted = new CountDownLatch(count);
        Map<String, AtomicInteger> modelCalls = new ConcurrentHashMap<>();
        Map<String, String> toolPrincipals = new ConcurrentHashMap<>();
        AgentRuntime runtime = AgentRuntime.builder((modelRequest, executionContext) -> {
            String runId = executionContext.agentRunId();
            assertThat(AgentRuntimeContexts.require(executionContext).principalId())
                    .isEqualTo("user-" + runId);
            int call = modelCalls.computeIfAbsent(runId, ignored -> new AtomicInteger())
                    .incrementAndGet();
            return ModelResponse.text(switch (call) {
                case 1 -> plan();
                case 2 -> toolCall("read-1", "read");
                default -> "answer-" + runId;
            });
        }).tools(context -> List.of(new AgentTool() {
            @Override
            public AgentToolDescriptor descriptor() {
                return AgentRuntimeFlowTest.descriptor("read", true);
            }

            @Override
            public ToolExecutionResult execute(Map<String, Object> arguments,
                                               AgentToolContext toolContext) {
                String runId = toolContext.executionContext().agentRunId();
                String principal = AgentRuntimeContexts.require(toolContext).principalId();
                toolPrincipals.put(runId, principal);
                allToolsStarted.countDown();
                await(allToolsStarted);
                return ToolExecutionResult.success(principal);
            }
        })).build();
        ExecutorService callers = Executors.newFixedThreadPool(count);
        try {
            List<Future<AgentResult>> futures = new ArrayList<>();
            for (int index = 0; index < count; index++) {
                String runId = "run-" + index;
                futures.add(callers.submit(() -> runtime.run(request(runId),
                        context("user-" + runId))));
            }
            for (int index = 0; index < count; index++) {
                String runId = "run-" + index;
                AgentResult result = futures.get(index).get(15, TimeUnit.SECONDS);
                assertThat(result.runStatus()).isEqualTo(AgentRunStatus.COMPLETED);
                assertThat(result.answer()).isEqualTo("answer-" + runId);
                assertThat(result.snapshot().agentRunId()).isEqualTo(runId);
                assertThat(result.snapshot().observations()).extracting(AgentObservation::output)
                        .contains("user-" + runId)
                        .doesNotContain("user-run-" + ((index + 1) % count));
                assertThat(toolPrincipals).containsEntry(runId, "user-" + runId);
            }
            assertThat(modelCalls).hasSize(count);
        } finally {
            callers.shutdownNow();
        }
    }

    private static AgentRequest request(String runId) {
        return AgentRequest.of(runId, "inspect", AgentJsonV1Codec.ID);
    }

    private static AgentBusinessContext context(String principalId) {
        return new AgentBusinessContext(principalId, "tenant-1", Set.of("reader"), Map.of());
    }

    private static AgentToolDescriptor descriptor(String name, boolean readOnly) {
        return new AgentToolDescriptor(name, "test tool", Map.of(), readOnly, false, Map.of());
    }

    private static String plan() {
        return envelope("""
                {"type":"task_plan","plan":"inspect","tasks":[
                  {"taskId":"inspect","title":"Inspect","dependsOn":[]}
                ]}
                """);
    }

    private static String toolCall(String callId, String toolName) {
        return envelope("""
                {"type":"tool_call","toolCall":{"callId":"%s","taskId":"inspect",
                  "toolName":"%s","arguments":{}}}
                """.formatted(callId, toolName));
    }

    private static String envelope(String json) {
        return AgentJsonV1Codec.START_MARKER + json + AgentJsonV1Codec.END_MARKER;
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("concurrent tool barrier timed out");
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("concurrent tool interrupted", error);
        }
    }
}
