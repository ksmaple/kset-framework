package com.kset.agent.core.loop;

import com.kset.agent.core.AgentCoreException;
import com.kset.agent.core.AgentErrorCode;
import com.kset.agent.core.action.AgentActionResult;
import com.kset.agent.core.action.AgentObservation;
import com.kset.agent.core.action.ExtensionAction;
import com.kset.agent.core.action.StandardActionHandlers;
import com.kset.agent.core.action.StandardActionTypes;
import com.kset.agent.core.api.AgentRequest;
import com.kset.agent.core.api.AgentResumeInput;
import com.kset.agent.core.api.AgentResult;
import com.kset.agent.core.api.AgentRunStatus;
import com.kset.agent.core.event.AgentLifecycleEventType;
import com.kset.agent.core.model.ModelRequest;
import com.kset.agent.core.model.ModelResponse;
import com.kset.agent.core.protocol.AgentDecision;
import com.kset.agent.core.protocol.AgentProtocolCodec;
import com.kset.agent.core.protocol.AgentProtocolContext;
import com.kset.agent.core.protocol.AgentProtocolId;
import com.kset.agent.core.stop.AgentStopDecision;
import com.kset.agent.core.stop.AgentStopReason;
import com.kset.agent.core.strategy.AgentReasoningStrategy;
import com.kset.agent.core.strategy.AgentTurn;
import com.kset.agent.core.tool.AgentTool;
import com.kset.agent.core.tool.AgentToolDescriptor;
import com.kset.agent.core.tool.InMemoryAgentToolRegistry;
import com.kset.agent.core.tool.ToolExecutionResult;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.kset.agent.core.loop.AgentTestSupport.envelope;
import static com.kset.agent.core.loop.AgentTestSupport.options;
import static com.kset.agent.core.loop.AgentTestSupport.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentLoopKernelFlowTest {

    @Test
    void completesPlanToolObservationAndFinalAnswerFlow() {
        AgentTestSupport.ScriptedModel model = new AgentTestSupport.ScriptedModel(
                envelope("""
                        {"type":"task_plan","plan":"inspect","tasks":[
                          {"taskId":"inspect","title":"Inspect source","dependsOn":[]}
                        ]}
                        """),
                envelope("""
                        {"type":"tool_call","toolCall":{
                          "callId":"inspect#1","taskId":"inspect",
                          "toolName":"search","arguments":{"query":"kernel"}
                        }}
                        """),
                envelope("{\"type\":\"final_answer\",\"answer\":\"done\"}"));
        AtomicInteger toolCalls = new AtomicInteger();
        InMemoryAgentToolRegistry tools = new InMemoryAgentToolRegistry()
                .register(tool("search", (arguments, context) -> {
                    toolCalls.incrementAndGet();
                    assertThat(context.operationId().runId()).isEqualTo("flow-run");
                    assertThat(context.operationId().callId()).isEqualTo("inspect#1");
                    return ToolExecutionResult.success(Map.of("hits", 1));
                }));
        AgentTestSupport.RecordingCheckpoint checkpoints =
                new AgentTestSupport.RecordingCheckpoint();
        AgentTestSupport.RecordingListener listener = new AgentTestSupport.RecordingListener();
        AgentLoopKernel kernel = AgentLoopKernel.builder(model)
                .tools(tools)
                .checkpoints(checkpoints)
                .listener(listener)
                .build();

        AgentResult result = kernel.run(request("flow-run"));

        assertThat(result.runStatus()).isEqualTo(AgentRunStatus.COMPLETED);
        assertThat(result.answer()).isEqualTo("done");
        assertThat(result.stopDecision().stopReason()).isEqualTo(AgentStopReason.COMPLETED);
        assertThat(result.snapshot().turn()).isEqualTo(3);
        assertThat(result.snapshot().observations())
                .extracting(AgentObservation::actionType)
                .containsExactly(StandardActionTypes.TASK_PLAN, StandardActionTypes.TOOL_CALL);
        assertThat(result.snapshot().observations().getLast().attributes())
                .containsEntry(AgentObservation.TOOL_CALL_ID, "inspect#1")
                .containsEntry(AgentObservation.TOOL_NAME, "search");
        assertThat(toolCalls).hasValue(1);
        assertThat(model.calls()).isEqualTo(3);
        assertThat(checkpoints.latest("flow-run").runStatus()).isEqualTo(AgentRunStatus.COMPLETED);

        List<AgentLifecycleEventType> eventTypes = listener.events().stream()
                .map(context -> context.eventType()).toList();
        assertThat(eventTypes.getFirst()).isEqualTo(AgentLifecycleEventType.RUN_STARTED);
        assertThat(eventTypes.getLast()).isEqualTo(AgentLifecycleEventType.RUN_RETURNED);
        assertThat(eventTypes).contains(
                AgentLifecycleEventType.MODEL_STARTED,
                AgentLifecycleEventType.MODEL_COMPLETED,
                AgentLifecycleEventType.DECISION_ACCEPTED,
                AgentLifecycleEventType.ACTION_STARTED,
                AgentLifecycleEventType.ACTION_COMPLETED,
                AgentLifecycleEventType.TURN_COMPLETED,
                AgentLifecycleEventType.CHECKPOINT_SAVED,
                AgentLifecycleEventType.RUN_STOPPED);
        assertThat(listener.events()).extracting(context -> context.eventSequence())
                .containsExactlyElementsOf(java.util.stream.LongStream
                        .rangeClosed(1, listener.events().size()).boxed().toList());
    }

    @Test
    void correctsProtocolErrorThenCompletes() {
        AgentTestSupport.ScriptedModel model = new AgentTestSupport.ScriptedModel(
                "{\"type\":\"final_answer\",\"answer\":\"missing markers\"}",
                "corrected answer");
        AgentTestSupport.RecordingListener listener = new AgentTestSupport.RecordingListener();
        AgentLoopKernel kernel = AgentLoopKernel.builder(model).listener(listener).build();

        AgentResult result = kernel.run(request("protocol-correction"));

        assertThat(result.runStatus()).isEqualTo(AgentRunStatus.COMPLETED);
        assertThat(result.answer()).isEqualTo("corrected answer");
        assertThat(result.snapshot().turn()).isEqualTo(2);
        assertThat(listener.protocolErrors()).isEqualTo(1);
    }

    @Test
    void stopsAtProtocolErrorLimit() {
        AtomicInteger modelCalls = new AtomicInteger();
        AgentLoopKernel kernel = AgentLoopKernel.builder((request, context) -> {
            modelCalls.incrementAndGet();
            return com.kset.agent.core.model.ModelResponse.text(
                    "{\"type\":\"final_answer\",\"answer\":\"missing markers\"}");
        }).build();
        AgentRequest request = request("protocol-limit",
                options(10, 2, 3, 8, 8), Map.of(), null);

        AgentResult result = kernel.run(request);

        assertThat(result.runStatus()).isEqualTo(AgentRunStatus.FAILED);
        assertThat(result.stopDecision().stopReason()).isEqualTo(AgentStopReason.PROTOCOL_ERROR_LIMIT);
        assertThat(modelCalls).hasValue(2);
    }

    @Test
    void suspendsForConfirmationAndConsumesConfirmedIdOnResume() {
        AgentTestSupport.ScriptedModel model = new AgentTestSupport.ScriptedModel(
                confirmation("confirm-1"), confirmation("confirm-1"), "confirmed answer");
        AgentLoopKernel kernel = AgentLoopKernel.builder(model).build();
        AgentRequest initial = request("confirmation-run");

        AgentResult suspended = kernel.run(initial);

        assertThat(suspended.runStatus()).isEqualTo(AgentRunStatus.SUSPENDED);
        assertThat(suspended.stopDecision().stopReason()).isEqualTo(AgentStopReason.WAITING_INPUT);
        assertThat(suspended.snapshot().attributes())
                .containsKey(StandardActionHandlers.PENDING_ACTION);

        AgentRequest confirmed = request("confirmation-run", initial.options(),
                Map.of(AgentRequest.CONFIRMED_ACTION_IDS, List.of("confirm-1")), null);
        AgentResult resumed = kernel.resume(confirmed, suspended.snapshot());

        assertThat(resumed.runStatus()).isEqualTo(AgentRunStatus.COMPLETED);
        assertThat(resumed.answer()).isEqualTo("confirmed answer");
        assertThat(resumed.snapshot().attributes())
                .doesNotContainKey(StandardActionHandlers.PENDING_ACTION);
        assertThat(resumed.snapshot().observations())
                .anyMatch(observation -> StandardActionTypes.CONFIRMATION.equals(
                        observation.actionType()));
    }

    @Test
    void requiresAuthoritativeResultsBeforeReconciliationResume() {
        AgentTestSupport.ScriptedModel model = new AgentTestSupport.ScriptedModel(
                envelope("""
                        {"type":"task_plan","plan":"write","tasks":[
                          {"taskId":"write","title":"Write value","dependsOn":[]}
                        ]}
                        """),
                envelope("""
                        {"type":"tool_call","toolCall":{
                          "callId":"write#1","taskId":"write",
                          "toolName":"writer","arguments":{"value":"x"}
                        }}
                        """),
                "reconciled answer");
        InMemoryAgentToolRegistry tools = new InMemoryAgentToolRegistry()
                .register(tool("writer", (arguments, context) -> ToolExecutionResult.failure(
                        AgentErrorCode.TOOL_RESULT_UNKNOWN, "remote result unknown")));
        AgentLoopKernel kernel = AgentLoopKernel.builder(model).tools(tools).build();
        AgentRequest request = request("reconcile-run");

        AgentResult suspended = kernel.run(request);

        assertThat(suspended.runStatus()).isEqualTo(AgentRunStatus.SUSPENDED);
        assertThat(suspended.stopDecision().stopReason())
                .isEqualTo(AgentStopReason.RECONCILIATION_REQUIRED);
        assertThat(suspended.snapshot().attributes())
                .containsKey(StandardActionHandlers.PENDING_ACTION);
        assertThatThrownBy(() -> kernel.resume(request, suspended.snapshot()))
                .isInstanceOfSatisfying(AgentCoreException.class,
                        error -> assertThat(error.errorCode())
                                .isEqualTo(AgentErrorCode.INVALID_RESUME_INPUT));

        AgentObservation wrong = AgentObservation.toolResult(
                "other#1", "write", "writer", ToolExecutionResult.success("saved"));
        assertThatThrownBy(() -> kernel.resume(
                request, suspended.snapshot(), new AgentResumeInput(List.of(wrong))))
                .isInstanceOfSatisfying(AgentCoreException.class,
                        error -> assertThat(error.errorCode())
                                .isEqualTo(AgentErrorCode.PENDING_ACTION_MISMATCH));

        AgentObservation authoritative = AgentObservation.toolResult(
                "write#1", "write", "writer", ToolExecutionResult.success("saved"));
        AgentResult resumed = kernel.resume(
                request, suspended.snapshot(), new AgentResumeInput(List.of(authoritative)));

        assertThat(resumed.runStatus()).isEqualTo(AgentRunStatus.COMPLETED);
        assertThat(resumed.answer()).isEqualTo("reconciled answer");
        assertThat(resumed.snapshot().attributes())
                .doesNotContainKey(StandardActionHandlers.PENDING_ACTION);
        assertThat(resumed.snapshot().observations().getLast().output()).isEqualTo("saved");
        assertThat(model.calls()).isEqualTo(3);
    }

    @Test
    void appliesCancellationTurnAndNoProgressStops() {
        AtomicInteger cancelledModelCalls = new AtomicInteger();
        AgentLoopKernel cancelledKernel = AgentLoopKernel.builder((request, context) -> {
            cancelledModelCalls.incrementAndGet();
            return com.kset.agent.core.model.ModelResponse.text("unused");
        }).build();
        AgentResult cancelled = cancelledKernel.run(request(
                "cancelled", AgentLoopOptions.defaults(), Map.of(), () -> true));
        assertThat(cancelled.runStatus()).isEqualTo(AgentRunStatus.CANCELLED);
        assertThat(cancelled.stopDecision().stopReason()).isEqualTo(AgentStopReason.USER_CANCELLED);
        assertThat(cancelledModelCalls).hasValue(0);

        AgentTestSupport.ScriptedModel turnModel = new AgentTestSupport.ScriptedModel(
                envelope("""
                        {"type":"task_plan","plan":"one turn","tasks":[
                          {"taskId":"one","title":"One","dependsOn":[]}
                        ]}
                        """));
        AgentLoopKernel turnKernel = AgentLoopKernel.builder(turnModel).build();
        AgentResult maxTurns = turnKernel.run(request(
                "max-turns", options(1, 3, 3, 8, 8), Map.of(), null));
        assertThat(maxTurns.stopDecision().stopReason()).isEqualTo(AgentStopReason.MAX_TURNS);

        AgentTestSupport.ScriptedModel noProgressModel = new AgentTestSupport.ScriptedModel(
                envelope("""
                        {"type":"task_plan","plan":"read","tasks":[
                          {"taskId":"read","title":"Read","dependsOn":[]}
                        ]}
                        """),
                envelope("""
                        {"type":"tool_call","toolCall":{
                          "callId":"read#1","taskId":"read",
                          "toolName":"reader","arguments":{}
                        }}
                        """));
        InMemoryAgentToolRegistry tools = new InMemoryAgentToolRegistry()
                .register(tool("reader", (arguments, context) ->
                        ToolExecutionResult.unchanged("same")));
        AgentLoopKernel noProgressKernel = AgentLoopKernel.builder(noProgressModel)
                .tools(tools).build();
        AgentResult noProgress = noProgressKernel.run(request(
                "no-progress", options(10, 3, 1, 8, 8), Map.of(), null));
        assertThat(noProgress.stopDecision().stopReason()).isEqualTo(AgentStopReason.NO_PROGRESS_LIMIT);
    }

    @Test
    void rejectsOversizedToolBatchBeforeExecution() {
        AgentTestSupport.ScriptedModel model = new AgentTestSupport.ScriptedModel(
                envelope("""
                        {"type":"task_plan","plan":"batch","tasks":[
                          {"taskId":"batch","title":"Batch","dependsOn":[]}
                        ]}
                        """),
                envelope("""
                        {"type":"tool_batch","plan":"three calls","toolCalls":[
                          {"callId":"b#1","taskId":"batch","toolName":"reader","arguments":{}},
                          {"callId":"b#2","taskId":"batch","toolName":"reader","arguments":{}},
                          {"callId":"b#3","taskId":"batch","toolName":"reader","arguments":{}}
                        ]}
                        """));
        AtomicInteger toolCalls = new AtomicInteger();
        InMemoryAgentToolRegistry tools = new InMemoryAgentToolRegistry()
                .register(tool("reader", (arguments, context) -> {
                    toolCalls.incrementAndGet();
                    return ToolExecutionResult.success("unused");
                }));
        AgentLoopKernel kernel = AgentLoopKernel.builder(model).tools(tools).build();

        AgentResult result = kernel.run(request(
                "capacity", options(10, 3, 3, 8, 2), Map.of(), null));

        assertThat(result.runStatus()).isEqualTo(AgentRunStatus.FAILED);
        assertThat(result.stopDecision().stopReason()).isEqualTo(AgentStopReason.CAPACITY_LIMIT);
        assertThat(toolCalls).hasValue(0);
    }

    @Test
    void stopsAfterReturnedModelWhenActiveDeadlineExpires() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-09T00:00:00Z"));
        AgentTestSupport.RecordingListener listener = new AgentTestSupport.RecordingListener();
        AgentLoopKernel kernel = AgentLoopKernel.builder((request, context) -> {
            clock.advance(Duration.ofSeconds(2));
            return com.kset.agent.core.model.ModelResponse.text("late answer");
        }).clock(clock).listener(listener).build();
        AgentLoopOptions options = new AgentLoopOptions(
                10, Duration.ofSeconds(1), 3, 3, 256,
                Duration.ofSeconds(1), Duration.ofSeconds(1), 8, 8);

        AgentResult result = kernel.run(request("timeout-run", options, Map.of(), null));

        assertThat(result.runStatus()).isEqualTo(AgentRunStatus.FAILED);
        assertThat(result.stopDecision().stopReason()).isEqualTo(AgentStopReason.TIMEOUT);
        assertThat(result.answer()).isNull();
        List<AgentLifecycleEventType> eventTypes = listener.events().stream()
                .map(context -> context.eventType()).toList();
        assertThat(eventTypes.indexOf(AgentLifecycleEventType.MODEL_COMPLETED))
                .isLessThan(eventTypes.indexOf(AgentLifecycleEventType.RUN_STOPPED));
    }

    @Test
    void returnsStructuredFailureAndPersistsFailureSnapshot() {
        AgentTestSupport.RecordingCheckpoint checkpoints =
                new AgentTestSupport.RecordingCheckpoint();
        AgentTestSupport.RecordingListener listener = new AgentTestSupport.RecordingListener();
        AgentLoopKernel kernel = AgentLoopKernel.builder((request, context) -> {
            throw new IllegalStateException("provider unavailable");
        }).checkpoints(checkpoints).listener(listener).build();

        AgentResult result = kernel.run(request("model-failure"));

        assertThat(result.runStatus()).isEqualTo(AgentRunStatus.FAILED);
        assertThat(result.stopDecision().stopReason()).isEqualTo(AgentStopReason.FATAL_ERROR);
        assertThat(result.failure()).isNotNull();
        assertThat(result.failure().errorCode()).isEqualTo(AgentErrorCode.MODEL_CALL_FAILED);
        assertThat(checkpoints.latest("model-failure").runStatus())
                .isEqualTo(AgentRunStatus.FAILED);
        assertThat(listener.events()).extracting(context -> context.eventType())
                .contains(AgentLifecycleEventType.RUN_FAILED,
                        AgentLifecycleEventType.RUN_STOPPED,
                        AgentLifecycleEventType.RUN_RETURNED);
    }

    @Test
    void reportsCheckpointFailureWithoutInvokingModel() {
        AtomicInteger modelCalls = new AtomicInteger();
        AgentLoopKernel kernel = AgentLoopKernel.builder((request, context) -> {
            modelCalls.incrementAndGet();
            return com.kset.agent.core.model.ModelResponse.text("unused");
        }).checkpoints((request, snapshot) -> {
            throw new IllegalStateException("store unavailable");
        }).build();

        AgentResult result = kernel.run(request("checkpoint-failure"));

        assertThat(result.runStatus()).isEqualTo(AgentRunStatus.FAILED);
        assertThat(result.stopDecision().stopReason()).isEqualTo(AgentStopReason.FATAL_ERROR);
        assertThat(result.failure()).isNotNull();
        assertThat(result.failure().errorCode()).isEqualTo(AgentErrorCode.CHECKPOINT_FAILED);
        assertThat(modelCalls).hasValue(0);
    }

    @Test
    void honorsHostStopPolicyBeforeCallingModel() {
        AtomicInteger modelCalls = new AtomicInteger();
        AgentLoopKernel kernel = AgentLoopKernel.builder((request, context) -> {
            modelCalls.incrementAndGet();
            return com.kset.agent.core.model.ModelResponse.text("unused");
        }).stopPolicy(context -> Optional.of(AgentStopDecision.stop(
                AgentStopReason.POLICY, AgentRunStatus.FAILED, "host quota reached")))
                .build();

        AgentResult result = kernel.run(request("policy-stop"));

        assertThat(result.runStatus()).isEqualTo(AgentRunStatus.FAILED);
        assertThat(result.stopDecision().stopReason()).isEqualTo(AgentStopReason.POLICY);
        assertThat(result.stopDecision().stopMessage()).isEqualTo("host quota reached");
        assertThat(modelCalls).hasValue(0);
    }

    @Test
    void preservesThreadInterruptionAndStopsBeforeCallingModel() {
        AtomicInteger modelCalls = new AtomicInteger();
        AgentLoopKernel kernel = AgentLoopKernel.builder((request, context) -> {
            modelCalls.incrementAndGet();
            return com.kset.agent.core.model.ModelResponse.text("unused");
        }).build();

        AgentResult result;
        try {
            Thread.currentThread().interrupt();
            result = kernel.run(request("interrupted-run"));
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }

        assertThat(result.runStatus()).isEqualTo(AgentRunStatus.CANCELLED);
        assertThat(result.stopDecision().stopReason()).isEqualTo(AgentStopReason.THREAD_INTERRUPTED);
        assertThat(modelCalls).hasValue(0);
    }

    @Test
    void executesOnlyTheExactPendingToolOperationAfterConfirmation() {
        AgentTestSupport.ScriptedModel model = new AgentTestSupport.ScriptedModel(
                envelope("""
                        {"type":"task_plan","plan":"write","tasks":[
                          {"taskId":"write","title":"Write","dependsOn":[]}
                        ]}
                        """),
                toolCall("write#1", "write", "writer", "x"),
                toolCall("write#1", "write", "writer", "x"),
                "written");
        AtomicInteger toolCalls = new AtomicInteger();
        AgentLoopKernel kernel = AgentLoopKernel.builder(model)
                .tools(new InMemoryAgentToolRegistry().register(dangerousTool(
                        "writer", (arguments, context) -> {
                            toolCalls.incrementAndGet();
                            return ToolExecutionResult.success(arguments.get("value"));
                        })))
                .build();
        AgentRequest initial = request("tool-confirmation");

        AgentResult suspended = kernel.run(initial);
        AgentRequest confirmed = request("tool-confirmation", initial.options(),
                Map.of(AgentRequest.CONFIRMED_ACTION_IDS, List.of("write#1")), null);
        AgentResult resumed = kernel.resume(confirmed, suspended.snapshot());

        assertThat(suspended.stopDecision().stopReason()).isEqualTo(AgentStopReason.WAITING_INPUT);
        assertThat(resumed.runStatus()).isEqualTo(AgentRunStatus.COMPLETED);
        assertThat(resumed.answer()).isEqualTo("written");
        assertThat(toolCalls).hasValue(1);
        assertThat(resumed.snapshot().attributes())
                .containsKey(StandardActionHandlers.CONFIRMED_TOOL_OPERATIONS);
    }

    @Test
    void rejectsChangedToolArgumentsAfterConfirmation() {
        AgentTestSupport.ScriptedModel model = new AgentTestSupport.ScriptedModel(
                envelope("""
                        {"type":"task_plan","plan":"write","tasks":[
                          {"taskId":"write","title":"Write","dependsOn":[]}
                        ]}
                        """),
                toolCall("write#1", "write", "writer", "x"),
                toolCall("write#1", "write", "writer", "changed"),
                "operation rejected");
        AtomicInteger toolCalls = new AtomicInteger();
        AgentTestSupport.RecordingListener listener = new AgentTestSupport.RecordingListener();
        AgentLoopKernel kernel = AgentLoopKernel.builder(model)
                .tools(new InMemoryAgentToolRegistry().register(dangerousTool(
                        "writer", (arguments, context) -> {
                            toolCalls.incrementAndGet();
                            return ToolExecutionResult.success("unexpected");
                        })))
                .listener(listener)
                .build();
        AgentRequest initial = request("changed-confirmation");
        AgentResult suspended = kernel.run(initial);
        AgentRequest confirmed = request("changed-confirmation", initial.options(),
                Map.of(AgentRequest.CONFIRMED_ACTION_IDS, List.of("write#1")), null);

        AgentResult resumed = kernel.resume(confirmed, suspended.snapshot());

        assertThat(resumed.runStatus()).isEqualTo(AgentRunStatus.COMPLETED);
        assertThat(resumed.answer()).isEqualTo("operation rejected");
        assertThat(listener.protocolErrors()).isEqualTo(1);
        assertThat(toolCalls).hasValue(0);
    }

    @Test
    void rejectsPreconfirmedToolWithoutPendingSnapshot() {
        AgentTestSupport.ScriptedModel model = new AgentTestSupport.ScriptedModel(
                envelope("""
                        {"type":"task_plan","plan":"write","tasks":[
                          {"taskId":"write","title":"Write","dependsOn":[]}
                        ]}
                        """),
                toolCall("write#1", "write", "writer", "x"));
        AtomicInteger toolCalls = new AtomicInteger();
        AgentLoopKernel kernel = AgentLoopKernel.builder(model)
                .tools(new InMemoryAgentToolRegistry().register(dangerousTool(
                        "writer", (arguments, context) -> {
                            toolCalls.incrementAndGet();
                            return ToolExecutionResult.success("unexpected");
                        })))
                .build();

        AgentResult result = kernel.run(request(
                "preconfirmed", AgentLoopOptions.defaults(),
                Map.of(AgentRequest.CONFIRMED_ACTION_IDS, List.of("write#1")), null));

        assertThat(result.runStatus()).isEqualTo(AgentRunStatus.FAILED);
        assertThat(result.failure().errorCode()).isEqualTo(AgentErrorCode.PENDING_ACTION_MISMATCH);
        assertThat(toolCalls).hasValue(0);
    }

    @Test
    void allowsRetryOfTheSameToolOperationIdentity() {
        AgentTestSupport.ScriptedModel model = new AgentTestSupport.ScriptedModel(
                envelope("""
                        {"type":"task_plan","plan":"read","tasks":[
                          {"taskId":"read","title":"Read","dependsOn":[]}
                        ]}
                        """),
                toolCall("read#1", "read", "reader", "x"),
                toolCall("read#1", "read", "reader", "x"),
                "read complete");
        AtomicInteger toolCalls = new AtomicInteger();
        AgentLoopKernel kernel = AgentLoopKernel.builder(model)
                .tools(new InMemoryAgentToolRegistry().register(tool(
                        "reader", (arguments, context) -> {
                            toolCalls.incrementAndGet();
                            return ToolExecutionResult.success(arguments.get("value"));
                        })))
                .build();

        AgentResult result = kernel.run(request("same-operation-retry"));

        assertThat(result.runStatus()).isEqualTo(AgentRunStatus.COMPLETED);
        assertThat(result.answer()).isEqualTo("read complete");
        assertThat(toolCalls).hasValue(2);
    }

    @Test
    void returnsCheckpointFailureWhenCancellationAndPersistenceFailureOverlap() {
        AgentLoopKernel kernel = AgentLoopKernel.builder((request, context) ->
                        ModelResponse.text("unused"))
                .checkpoints((request, snapshot) -> {
                    throw new IllegalStateException("store unavailable");
                })
                .build();

        AgentResult result = kernel.run(request(
                "cancel-checkpoint", AgentLoopOptions.defaults(), Map.of(), () -> true));

        assertThat(result.runStatus()).isEqualTo(AgentRunStatus.FAILED);
        assertThat(result.stopDecision().stopReason()).isEqualTo(AgentStopReason.FATAL_ERROR);
        assertThat(result.failure().errorCode()).isEqualTo(AgentErrorCode.CHECKPOINT_FAILED);
    }

    @Test
    void rejectsPolicyCompletionWithoutFinalAnswer() {
        AtomicInteger modelCalls = new AtomicInteger();
        AgentLoopKernel kernel = AgentLoopKernel.builder((request, context) -> {
            modelCalls.incrementAndGet();
            return ModelResponse.text("unused");
        }).stopPolicy(context -> Optional.of(AgentStopDecision.stop(
                AgentStopReason.POLICY, AgentRunStatus.COMPLETED, "invalid completion")))
                .build();

        AgentResult result = kernel.run(request("invalid-policy-completion"));

        assertThat(result.runStatus()).isEqualTo(AgentRunStatus.FAILED);
        assertThat(result.stopDecision().stopReason()).isEqualTo(AgentStopReason.FATAL_ERROR);
        assertThat(result.failure().errorCode())
                .isEqualTo(AgentErrorCode.EXTENSION_CONTRACT_VIOLATION);
        assertThat(modelCalls).hasValue(0);
    }

    @Test
    void keepsCustomUnknownResultOnTheExtensionSuspensionPath() {
        AgentProtocolId protocolId = new AgentProtocolId("custom", "v1");
        AgentProtocolCodec codec = new AgentProtocolCodec() {
            @Override
            public AgentProtocolId protocolId() {
                return protocolId;
            }

            @Override
            public AgentDecision decode(ModelResponse response, AgentProtocolContext context) {
                return AgentDecision.of(new ExtensionAction("custom_wait", Map.of()));
            }
        };
        AgentReasoningStrategy strategy = new AgentReasoningStrategy() {
            @Override
            public String strategyId() {
                return "custom";
            }

            @Override
            public AgentTurn nextTurn(AgentRequest request, AgentRunState state) {
                return new AgentTurn(protocolId,
                        new ModelRequest("", request.task(), 32, Map.of()));
            }
        };
        AgentLoopKernel kernel = AgentLoopKernel.builder((request, context) ->
                        ModelResponse.text("custom"))
                .protocol(codec)
                .strategy(strategy)
                .actionHandler(new com.kset.agent.core.action.AgentActionHandler() {
                    @Override
                    public String actionType() {
                        return "custom_wait";
                    }

                    @Override
                    public AgentActionResult handle(
                            com.kset.agent.core.action.AgentAction action,
                            com.kset.agent.core.action.AgentActionContext context) {
                        return new AgentActionResult(
                                List.of(AgentObservation.failure(
                                        action.actionType(), AgentErrorCode.TOOL_RESULT_UNKNOWN,
                                        "custom result unknown")),
                                AgentRunStatus.SUSPENDED, "custom wait",
                                Map.of("custom.pending", true), Set.of());
                    }
                })
                .build();
        AgentRequest customRequest = new AgentRequest(
                "custom-unknown", "custom task", protocolId, AgentLoopOptions.defaults(),
                Map.of(), null);

        AgentResult result = kernel.run(customRequest);

        assertThat(result.runStatus()).isEqualTo(AgentRunStatus.SUSPENDED);
        assertThat(result.stopDecision().stopReason()).isEqualTo(AgentStopReason.WAITING_INPUT);
        assertThat(result.snapshot().attributes()).containsEntry("custom.pending", true);
        assertThat(result.snapshot().attributes())
                .doesNotContainKey(StandardActionHandlers.PENDING_ACTION);
    }

    private static AgentTool tool(String name, ToolFunction function) {
        return tool(name, true, false, function);
    }

    private static AgentTool dangerousTool(String name, ToolFunction function) {
        return tool(name, false, true, function);
    }

    private static AgentTool tool(
            String name, boolean readOnly, boolean requiresConfirmation,
            ToolFunction function) {
        return new AgentTool() {
            @Override
            public AgentToolDescriptor descriptor() {
                return new AgentToolDescriptor(
                        name, name + " test tool", Map.of(), readOnly,
                        requiresConfirmation, Map.of());
            }

            @Override
            public ToolExecutionResult execute(
                    Map<String, Object> arguments,
                    com.kset.agent.core.tool.AgentToolContext context) {
                return function.execute(arguments, context);
            }
        };
    }

    private static String toolCall(
            String callId, String taskId, String toolName, String value) {
        return envelope("""
                {"type":"tool_call","toolCall":{
                  "callId":"%s","taskId":"%s","toolName":"%s",
                  "arguments":{"value":"%s"}
                }}
                """.formatted(callId, taskId, toolName, value));
    }

    private static String confirmation(String id) {
        return envelope("""
                {"type":"confirmation","confirmation":{
                  "confirmationId":"%s","message":"confirm operation","options":{}
                }}
                """.formatted(id));
    }

    @FunctionalInterface
    private interface ToolFunction {
        ToolExecutionResult execute(
                Map<String, Object> arguments,
                com.kset.agent.core.tool.AgentToolContext context);
    }

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> instant;

        private MutableClock(Instant instant) {
            this.instant = new AtomicReference<>(instant);
        }

        private void advance(Duration duration) {
            instant.updateAndGet(value -> value.plus(duration));
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant.get();
        }
    }
}
