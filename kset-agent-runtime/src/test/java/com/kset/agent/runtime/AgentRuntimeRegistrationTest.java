package com.kset.agent.runtime;

import com.kset.agent.core.api.AgentRequest;
import com.kset.agent.core.api.AgentResult;
import com.kset.agent.core.api.AgentRunStatus;
import com.kset.agent.core.event.AgentLifecycleContext;
import com.kset.agent.core.event.AgentLifecycleEventType;
import com.kset.agent.core.model.ModelCallMetrics;
import com.kset.agent.core.model.ModelResponse;
import com.kset.agent.core.protocol.json.AgentJsonV1Codec;
import com.kset.agent.core.tool.AgentTool;
import com.kset.agent.core.tool.AgentToolContext;
import com.kset.agent.core.tool.AgentToolDescriptor;
import com.kset.agent.core.tool.ToolExecutionResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentRuntimeRegistrationTest {

    @Test
    void registersDirectToolsAndSelectedSkillWithContextAndObservabilityCallbacks() {
        AtomicInteger modelCalls = new AtomicInteger();
        List<String> executedTools = new ArrayList<>();
        List<AgentLifecycleContext> events = new ArrayList<>();
        List<ModelCallMetrics> reportedMetrics = new ArrayList<>();
        List<AgentLifecycleContext> metricEvents = new ArrayList<>();
        ModelCallMetrics metrics = new ModelCallMetrics("provider", "model", "request-1",
                10L, 5L, 15L, Map.of());
        AgentTool directTool = tool("direct", executedTools);
        AgentRuntime runtime = AgentRuntime.builder((modelRequest, executionContext) -> {
            int call = modelCalls.incrementAndGet();
            return switch (call) {
                case 1 -> new ModelResponse(plan(), List.of(), null, metrics, Map.of());
                case 2 -> ModelResponse.text(toolCall("call-1", "direct"));
                case 3 -> ModelResponse.text(toolCall("call-2", "inline"));
                case 4 -> ModelResponse.text(toolCall("call-3", "selected"));
                default -> ModelResponse.text("finished");
            };
        }).tool(directTool)
                .tool(descriptor("inline"), (arguments, toolContext) -> {
                    executedTools.add("inline");
                    assertThat(AgentRuntimeContexts.require(toolContext).principalId())
                            .isEqualTo("user-1");
                    return ToolExecutionResult.success("inline-result");
                })
                .skill("selected-skill", context -> {
                    assertThat(context.roles()).contains("reader");
                    return List.of(tool("selected", executedTools));
                })
                .onEvent(events::add)
                .onModelMetrics((event, value) -> {
                    metricEvents.add(event);
                    reportedMetrics.add(value);
                })
                .build();

        AgentResult result = runtime.run(request(), context(), List.of("selected-skill"));

        assertThat(result.runStatus()).isEqualTo(AgentRunStatus.COMPLETED);
        assertThat(executedTools).containsExactly("direct", "inline", "selected");
        assertThat(reportedMetrics).containsExactly(metrics);
        assertThat(metricEvents).hasSize(1);
        assertThat(metricEvents.getFirst().eventType())
                .isEqualTo(AgentLifecycleEventType.MODEL_COMPLETED);
        assertThat(events).extracting(AgentLifecycleContext::eventSequence)
                .isSortedAccordingTo(Long::compareTo);
        assertThat(events).extracting(AgentLifecycleContext::eventType)
                .contains(AgentLifecycleEventType.RUN_STARTED,
                        AgentLifecycleEventType.MODEL_COMPLETED,
                        AgentLifecycleEventType.RUN_RETURNED);
    }

    @Test
    void rejectsDuplicateNamesAcrossDirectToolsAndSelectedSkill() {
        AgentRuntime runtime = AgentRuntime.builder((request, context) -> ModelResponse.text("done"))
                .tool(tool("shared", new ArrayList<>()))
                .skill("duplicate-skill", context -> List.of(tool("shared", new ArrayList<>())))
                .build();

        assertThatThrownBy(() -> runtime.run(request(), context(), List.of("duplicate-skill")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate tool name");
        assertThatThrownBy(() -> AgentRuntime.builder((request, context) ->
                ModelResponse.text("done"))
                .skill("same", context -> List.of())
                .skill("same", context -> List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate skill");
    }

    private static AgentTool tool(String name, List<String> executedTools) {
        return new AgentTool() {
            @Override
            public AgentToolDescriptor descriptor() {
                return AgentRuntimeRegistrationTest.descriptor(name);
            }

            @Override
            public ToolExecutionResult execute(Map<String, Object> arguments,
                                               AgentToolContext context) {
                executedTools.add(name);
                assertThat(AgentRuntimeContexts.require(context).principalId()).isEqualTo("user-1");
                return ToolExecutionResult.success(name + "-result");
            }
        };
    }

    private static AgentToolDescriptor descriptor(String name) {
        return new AgentToolDescriptor(name, "test tool", Map.of(), true, false, Map.of());
    }

    private static AgentRequest request() {
        return AgentRequest.of("registration-run", "inspect", AgentJsonV1Codec.ID);
    }

    private static AgentBusinessContext context() {
        return new AgentBusinessContext("user-1", "tenant-1", Set.of("reader"), Map.of());
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
}
