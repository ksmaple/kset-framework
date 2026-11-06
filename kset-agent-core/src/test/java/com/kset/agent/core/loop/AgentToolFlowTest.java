package com.kset.agent.core.loop;

import com.kset.agent.core.action.AgentObservation;
import com.kset.agent.core.action.ToolFlowActionHandler;
import com.kset.agent.core.api.AgentRequest;
import com.kset.agent.core.api.AgentResult;
import com.kset.agent.core.api.AgentRunStatus;
import com.kset.agent.core.protocol.json.AgentJsonFlowV1Codec;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

import static com.kset.agent.core.loop.AgentTestSupport.envelope;
import static org.assertj.core.api.Assertions.assertThat;

class AgentToolFlowTest {

    @Test
    void stopsOversizedFlowBeforeExecutingTools() {
        AtomicInteger calls = new AtomicInteger();
        InMemoryAgentToolRegistry tools = new InMemoryAgentToolRegistry()
                .register(tool("read", true, (arguments, context) -> {
                    calls.incrementAndGet();
                    return ToolExecutionResult.success("value");
                }));
        AgentLoopKernel kernel = kernel(tools, """
                {"type":"tool_flow","plan":"inspect","toolCalls":[
                  {"callId":"a","taskId":"inspect","toolName":"read",
                   "dependsOn":["b"],"arguments":{}},
                  {"callId":"b","taskId":"inspect","toolName":"read",
                   "dependsOn":["a"],"arguments":{}},
                  {"callId":"c","taskId":"inspect","toolName":"read","arguments":{}}
                ]}
                """);
        AgentLoopOptions options = new AgentLoopOptions(20, Duration.ofMinutes(5),
                3, 3, 0, Duration.ofMinutes(2), Duration.ofMinutes(2), 8, 2);
        AgentRequest request = new AgentRequest("flow-capacity", "inspect",
                AgentJsonFlowV1Codec.ID, options, Map.of(), null);

        AgentResult result = kernel.run(request);

        assertThat(result.runStatus()).isEqualTo(AgentRunStatus.FAILED);
        assertThat(result.stopDecision().stopReason()).isEqualTo(AgentStopReason.CAPACITY_LIMIT);
        assertThat(calls).hasValue(0);
    }

    @Test
    void standardToolCannotReuseObservedFlowCallId() {
        AtomicInteger writeCalls = new AtomicInteger();
        InMemoryAgentToolRegistry tools = new InMemoryAgentToolRegistry()
                .register(tool("read", true, (arguments, context) ->
                        ToolExecutionResult.success("value")))
                .register(tool("write", false, (arguments, context) -> {
                    writeCalls.incrementAndGet();
                    return ToolExecutionResult.success("written");
                }));
        AgentTestSupport.RecordingListener listener = new AgentTestSupport.RecordingListener();
        AgentLoopKernel kernel = AgentLoopKernel.builder(new AgentTestSupport.ScriptedModel(
                envelope("""
                        {"type":"task_plan","plan":"inspect","tasks":[
                          {"taskId":"inspect","title":"Inspect","dependsOn":[]}]}
                        """), envelope("""
                        {"type":"tool_flow","plan":"inspect","toolCalls":[
                          {"callId":"reused","taskId":"inspect","toolName":"read","arguments":{}},
                          {"callId":"other","taskId":"inspect","toolName":"read","arguments":{}}
                        ]}
                        """), envelope("""
                        {"type":"tool_call","toolCall":{"callId":"reused",
                         "taskId":"inspect","toolName":"write","arguments":{}}}
                        """), "done"))
                .tools(tools).readOnlyToolFlow().listener(listener).build();

        AgentResult result = kernel.run(request("flow-call-id-conflict"));

        assertThat(result.runStatus()).isEqualTo(AgentRunStatus.COMPLETED);
        assertThat(listener.protocolErrors()).isEqualTo(1);
        assertThat(writeCalls).hasValue(0);
    }

    @Test
    void executesIndependentReadsTogetherAndResolvesDependentArguments() {
        CountDownLatch rootsStarted = new CountDownLatch(2);
        AtomicInteger detailCalls = new AtomicInteger();
        InMemoryAgentToolRegistry tools = new InMemoryAgentToolRegistry()
                .register(tool("find-a", true, (arguments, context) -> {
                    rootsStarted.countDown();
                    await(rootsStarted);
                    return ToolExecutionResult.success(Map.of("id", "A"));
                }))
                .register(tool("find-b", true, (arguments, context) -> {
                    rootsStarted.countDown();
                    await(rootsStarted);
                    return ToolExecutionResult.success(Map.of("id", "B"));
                }))
                .register(tool("details", true, (arguments, context) -> {
                    detailCalls.incrementAndGet();
                    assertThat(rootsStarted.getCount()).isZero();
                    assertThat(arguments).containsEntry("ids", List.of("A", "B"));
                    return ToolExecutionResult.success("details found");
                }));
        AgentLoopKernel kernel = kernel(tools, """
                {"type":"tool_flow","plan":"find details","toolCalls":[
                  {"callId":"a","taskId":"inspect","toolName":"find-a","arguments":{}},
                  {"callId":"b","taskId":"inspect","toolName":"find-b","arguments":{}},
                  {"callId":"details","taskId":"inspect","toolName":"details",
                   "dependsOn":["a","b"],"arguments":{"ids":[
                     {"$toolResult":{"callId":"a","path":"/id"}},
                     {"$toolResult":{"callId":"b","path":"/id"}}]}}
                ]}
                """);

        AgentResult result = kernel.run(request("flow-parallel"));

        assertThat(result.runStatus()).isEqualTo(AgentRunStatus.COMPLETED);
        assertThat(detailCalls).hasValue(1);
        assertThat(toolObservations(result)).allMatch(AgentObservation::success);
        assertThat(toolObservations(result)).extracting(observation ->
                observation.attributes().get(AgentObservation.TOOL_CALL_ID))
                .containsExactly("a", "b", "details");
    }

    @Test
    void failedDependencyBlocksOnlyItsDescendants() {
        AtomicInteger blockedCalls = new AtomicInteger();
        InMemoryAgentToolRegistry tools = new InMemoryAgentToolRegistry()
                .register(tool("failed", true, (arguments, context) ->
                        ToolExecutionResult.failure("NOT_FOUND", "missing")))
                .register(tool("independent", true, (arguments, context) ->
                        ToolExecutionResult.success("available")))
                .register(tool("blocked", true, (arguments, context) -> {
                    blockedCalls.incrementAndGet();
                    return ToolExecutionResult.success("unexpected");
                }));
        AgentLoopKernel kernel = kernel(tools, """
                {"type":"tool_flow","plan":"inspect","toolCalls":[
                  {"callId":"first","taskId":"inspect","toolName":"failed","arguments":{}},
                  {"callId":"other","taskId":"inspect","toolName":"independent","arguments":{}},
                  {"callId":"last","taskId":"inspect","toolName":"blocked",
                   "dependsOn":["first"],"arguments":{}}
                ]}
                """);

        AgentResult result = kernel.run(request("flow-failure"));

        assertThat(result.runStatus()).isEqualTo(AgentRunStatus.COMPLETED);
        assertThat(blockedCalls).hasValue(0);
        assertThat(toolObservations(result)).extracting(AgentObservation::errorCode)
                .containsExactly("NOT_FOUND", null, ToolFlowActionHandler.DEPENDENCY_FAILED);
    }

    @Test
    void missingResultReferenceDoesNotCallDependentTool() {
        AtomicInteger dependentCalls = new AtomicInteger();
        InMemoryAgentToolRegistry tools = new InMemoryAgentToolRegistry()
                .register(tool("source", true, (arguments, context) ->
                        ToolExecutionResult.success(Map.of("other", "value"))))
                .register(tool("dependent", true, (arguments, context) -> {
                    dependentCalls.incrementAndGet();
                    return ToolExecutionResult.success("unexpected");
                }));
        AgentLoopKernel kernel = kernel(tools, """
                {"type":"tool_flow","plan":"inspect","toolCalls":[
                  {"callId":"source","taskId":"inspect","toolName":"source","arguments":{}},
                  {"callId":"dependent","taskId":"inspect","toolName":"dependent",
                   "dependsOn":["source"],"arguments":{"id":
                     {"$toolResult":{"callId":"source","path":"/id"}}}}
                ]}
                """);

        AgentResult result = kernel.run(request("flow-missing-reference"));

        assertThat(dependentCalls).hasValue(0);
        assertThat(toolObservations(result).getLast().errorCode())
                .isEqualTo(ToolFlowActionHandler.REFERENCE_MISSING);
    }

    @Test
    void rejectsWriteToolBeforeDispatchingAnyCall() {
        AtomicInteger calls = new AtomicInteger();
        InMemoryAgentToolRegistry tools = new InMemoryAgentToolRegistry()
                .register(tool("read", true, (arguments, context) -> {
                    calls.incrementAndGet();
                    return ToolExecutionResult.success("read");
                }))
                .register(tool("write", false, (arguments, context) -> {
                    calls.incrementAndGet();
                    return ToolExecutionResult.success("write");
                }));
        AgentLoopKernel kernel = kernel(tools, """
                {"type":"tool_flow","plan":"inspect","toolCalls":[
                  {"callId":"read","taskId":"inspect","toolName":"read","arguments":{}},
                  {"callId":"write","taskId":"inspect","toolName":"write","arguments":{}}
                ]}
                """);

        AgentResult result = kernel.run(request("flow-write"));

        assertThat(calls).hasValue(0);
        assertThat(toolObservations(result)).extracting(AgentObservation::errorCode)
                .containsExactly(ToolFlowActionHandler.READ_ONLY_REQUIRED,
                        ToolFlowActionHandler.READ_ONLY_REQUIRED);
    }

    @Test
    void rejectsFlowThatIgnoresPlanTaskDependency() {
        AtomicInteger calls = new AtomicInteger();
        InMemoryAgentToolRegistry tools = new InMemoryAgentToolRegistry()
                .register(tool("read", true, (arguments, context) -> {
                    calls.incrementAndGet();
                    return ToolExecutionResult.success("value");
                }));
        AgentLoopKernel kernel = AgentLoopKernel.builder(
                new AgentTestSupport.ScriptedModel(envelope("""
                        {"type":"task_plan","plan":"inspect","tasks":[
                          {"taskId":"first","title":"First","dependsOn":[]},
                          {"taskId":"second","title":"Second","dependsOn":["first"]}]}
                        """), envelope("""
                        {"type":"tool_flow","plan":"inspect","toolCalls":[
                          {"callId":"a","taskId":"first","toolName":"read","arguments":{}},
                          {"callId":"b","taskId":"second","toolName":"read","arguments":{}}
                        ]}
                        """), "done"))
                .tools(tools).readOnlyToolFlow().build();

        AgentResult result = kernel.run(request("flow-plan-dependency"));

        assertThat(calls).hasValue(0);
        assertThat(toolObservations(result)).extracting(AgentObservation::errorCode)
                .containsExactly("INVALID_TOOL_FLOW", "INVALID_TOOL_FLOW");
    }

    @Test
    void cyclicFlowIsRejectedAsProtocolError() {
        AtomicInteger calls = new AtomicInteger();
        InMemoryAgentToolRegistry tools = new InMemoryAgentToolRegistry()
                .register(tool("read", true, (arguments, context) -> {
                    calls.incrementAndGet();
                    return ToolExecutionResult.success("value");
                }));
        AgentTestSupport.RecordingListener listener = new AgentTestSupport.RecordingListener();
        AgentLoopKernel kernel = AgentLoopKernel.builder(new AgentTestSupport.ScriptedModel(
                envelope("""
                        {"type":"task_plan","plan":"inspect","tasks":[
                          {"taskId":"inspect","title":"Inspect","dependsOn":[]}]}
                        """), envelope("""
                        {"type":"tool_flow","plan":"inspect","toolCalls":[
                          {"callId":"a","taskId":"inspect","toolName":"read",
                           "dependsOn":["b"],"arguments":{}},
                          {"callId":"b","taskId":"inspect","toolName":"read",
                           "dependsOn":["a"],"arguments":{}}
                        ]}
                        """), "done"))
                .tools(tools).readOnlyToolFlow().listener(listener).build();

        AgentResult result = kernel.run(request("flow-cycle"));

        assertThat(result.runStatus()).isEqualTo(AgentRunStatus.COMPLETED);
        assertThat(listener.protocolErrors()).isEqualTo(1);
        assertThat(calls).hasValue(0);
    }

    private static AgentLoopKernel kernel(InMemoryAgentToolRegistry tools, String action) {
        return AgentLoopKernel.builder(new AgentTestSupport.ScriptedModel(
                envelope("""
                        {"type":"task_plan","plan":"inspect","tasks":[
                          {"taskId":"inspect","title":"Inspect","dependsOn":[]}]}
                        """), envelope(action), "done"))
                .tools(tools).readOnlyToolFlow().build();
    }

    private static AgentRequest request(String runId) {
        return AgentRequest.of(runId, "inspect", AgentJsonFlowV1Codec.ID);
    }

    private static List<AgentObservation> toolObservations(AgentResult result) {
        return result.snapshot().observations().stream()
                .filter(observation -> observation.attributes()
                        .containsKey(AgentObservation.TOOL_CALL_ID))
                .toList();
    }

    private static AgentTool tool(String name, boolean readOnly,
                                  BiFunction<Map<String, Object>, AgentToolContext,
                                          ToolExecutionResult> handler) {
        return new AgentTool() {
            @Override
            public AgentToolDescriptor descriptor() {
                return new AgentToolDescriptor(name, "test tool", Map.of(),
                        readOnly, false, Map.of());
            }

            @Override
            public ToolExecutionResult execute(Map<String, Object> arguments,
                                               AgentToolContext context) {
                return handler.apply(arguments, context);
            }
        };
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("tool reads did not overlap");
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("tool read interrupted", error);
        }
    }
}
