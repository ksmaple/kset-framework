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
import com.kset.agent.core.event.AgentLifecycleContext;
import com.kset.agent.core.event.AgentLifecycleEventType;
import com.kset.agent.core.event.AgentLifecycleListener;
import com.kset.agent.core.event.AgentInvocationType;
import com.kset.agent.core.model.AgentModelRetryOptions;
import com.kset.agent.core.model.ModelRequest;
import com.kset.agent.core.model.ModelResponse;
import com.kset.agent.core.protocol.AgentDecision;
import com.kset.agent.core.protocol.AgentProtocolCodec;
import com.kset.agent.core.protocol.AgentProtocolContext;
import com.kset.agent.core.protocol.AgentProtocolId;
import com.kset.agent.core.protocol.json.AgentJsonV1ErrorCode;
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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.kset.agent.core.loop.AgentTestSupport.assertLifecycleTrace;
import static com.kset.agent.core.loop.AgentTestSupport.envelope;
import static com.kset.agent.core.loop.AgentTestSupport.options;
import static com.kset.agent.core.loop.AgentTestSupport.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentLoopKernelFlowTest {

    @Test
    void rejectsLegacySnapshotsAndSuspendedAnswer() {
        Instant now = Instant.parse("2026-10-10T00:00:00Z");
        AgentProtocolId protocolId = new AgentProtocolId("agent-json", "v1");
        assertThatThrownBy(() -> new AgentRunSnapshot(
                1, "legacy-run", "task", protocolId, AgentRunStatus.RUNNING,
                0, now, now, List.of(), Map.of(), 0, 0, null, null, null))
                .isInstanceOfSatisfying(AgentCoreException.class,
                        error -> assertThat(error.errorCode())
                                .isEqualTo(AgentErrorCode.UNSUPPORTED_SNAPSHOT_VERSION));
        assertThatThrownBy(() -> new AgentRunSnapshot(
                2, "legacy-run", "task", protocolId, AgentRunStatus.RUNNING,
                0, now, now, List.of(), Map.of(), 0, 0, null, null, null))
                .isInstanceOfSatisfying(AgentCoreException.class,
                        error -> assertThat(error.errorCode())
                                .isEqualTo(AgentErrorCode.UNSUPPORTED_SNAPSHOT_VERSION));
        assertThatThrownBy(() -> new AgentRunSnapshot(
                3, "legacy-run", "task", protocolId, AgentRunStatus.RUNNING,
                0, now, now, List.of(), Map.of(), 0, 0, null, null, null))
                .isInstanceOfSatisfying(AgentCoreException.class,
                        error -> assertThat(error.errorCode())
                                .isEqualTo(AgentErrorCode.UNSUPPORTED_SNAPSHOT_VERSION));

        AgentStopDecision stopped = AgentStopDecision.stop(
                AgentStopReason.WAITING_INPUT, AgentRunStatus.SUSPENDED, "confirm");
        assertThatThrownBy(() -> new AgentRunSnapshot(
                AgentRunSnapshot.CURRENT_VERSION, "paused-run", "task", protocolId,
                AgentRunStatus.SUSPENDED, 1, now, now, List.of(), Map.of(),
                0, 0, "confirm", null, stopped))
                .isInstanceOfSatisfying(AgentCoreException.class,
                        error -> assertThat(error.errorCode())
                                .isEqualTo(AgentErrorCode.INVALID_SNAPSHOT));
    }

    @Test
    void resumesARunningCheckpointWithoutAStopDecision() {
        Instant now = Instant.parse("2024-01-01T00:00:00Z");
        AgentRequest request = request("running-checkpoint");
        AgentRunSnapshot snapshot = new AgentRunSnapshot(
                AgentRunSnapshot.CURRENT_VERSION, request.agentRunId(), request.task(),
                request.protocolId(), AgentRunStatus.RUNNING, 0, now, now,
                List.of(), Map.of(), 0, 0, null, null, null);
        AgentLoopKernel kernel = AgentLoopKernel.builder((modelRequest, context) ->
                ModelResponse.text("resumed answer")).build();

        AgentResult result = kernel.resume(request, snapshot);

        assertThat(result.runStatus()).isEqualTo(AgentRunStatus.COMPLETED);
        assertThat(result.answer()).isEqualTo("resumed answer");
    }

    @Test
    void rejectsInconsistentPendingSnapshotState() {
        AgentRequest request = request("invalid-pending-snapshot");
        Instant now = Instant.parse("2026-10-10T00:00:00Z");
        Map<String, Object> firstCall = Map.of(
                "callId", "first", "toolName", "write", "arguments", Map.of());
        Map<String, Object> secondCall = Map.of(
                "callId", "second", "toolName", "write", "arguments", Map.of());
        Map<String, Object> thirdCall = Map.of(
                "callId", "third", "toolName", "write", "arguments", Map.of());
        Map<String, Object> pendingCall = Map.of(
                "type", StandardActionTypes.TOOL_CALL,
                "callId", "first", "toolName", "write", "arguments", Map.of());
        Map<String, Object> pendingBatch = Map.of(
                "type", StandardActionTypes.TOOL_BATCH,
                "summary", "write both", "toolCalls", List.of(firstCall, secondCall));
        AgentStopDecision reconciliationStop = AgentStopDecision.stop(
                AgentStopReason.RECONCILIATION_REQUIRED, AgentRunStatus.SUSPENDED,
                "reconcile");
        AgentStopDecision waitingStop = AgentStopDecision.stop(
                AgentStopReason.WAITING_INPUT, AgentRunStatus.SUSPENDED, "approve");

        List<Map<String, Object>> invalidRunningAttributes = List.of(
                Map.of(StandardActionHandlers.PENDING_BATCH, List.of(firstCall, secondCall)),
                Map.of(StandardActionHandlers.PENDING_ACTION, pendingCall,
                        StandardActionHandlers.PENDING_BATCH, List.of(secondCall, thirdCall)),
                Map.of(StandardActionHandlers.PENDING_ACTION, pendingCall,
                        StandardActionHandlers.PENDING_BATCH, List.of(firstCall, firstCall)),
                Map.of(StandardActionHandlers.PENDING_ACTION, pendingCall,
                        StandardActionHandlers.RECONCILIATION_PENDING, false),
                Map.of(StandardActionHandlers.RECONCILIATION_PENDING, true),
                Map.of(StandardActionHandlers.PENDING_ACTION, pendingBatch),
                Map.of(StandardActionHandlers.PENDING_ACTION, Map.of(
                        "type", StandardActionTypes.TOOL_CALL,
                        "callId", "first", "arguments", Map.of())),
                Map.of(StandardActionHandlers.PENDING_ACTION, Map.of(
                        "type", StandardActionTypes.TOOL_CALL,
                        "callId", "first", "toolName", "write", "arguments", "invalid")),
                Map.of(StandardActionHandlers.PENDING_ACTION, Map.of(
                        "type", StandardActionTypes.CONFIRMATION,
                        "message", "approve", "options", Map.of())),
                Map.of(StandardActionHandlers.PENDING_ACTION, pendingCall,
                        StandardActionHandlers.PENDING_BATCH, List.of(firstCall, Map.of(
                                "callId", "second", "toolName", "write"))));
        for (Map<String, Object> attributes : invalidRunningAttributes) {
            assertThatThrownBy(() -> new AgentRunSnapshot(
                    AgentRunSnapshot.CURRENT_VERSION, request.agentRunId(), request.task(),
                    request.protocolId(), AgentRunStatus.RUNNING, 1, now, now,
                    List.of(), attributes, 0, 0, null, null, null))
                    .isInstanceOfSatisfying(AgentCoreException.class,
                            error -> assertThat(error.errorCode())
                                    .isEqualTo(AgentErrorCode.INVALID_SNAPSHOT));
        }
        assertThatThrownBy(() -> new AgentRunSnapshot(
                AgentRunSnapshot.CURRENT_VERSION, request.agentRunId(), request.task(),
                request.protocolId(), AgentRunStatus.SUSPENDED, 1, now, now,
                List.of(), Map.of(StandardActionHandlers.PENDING_ACTION, pendingCall),
                0, 0, null, "reconcile", reconciliationStop))
                .isInstanceOfSatisfying(AgentCoreException.class,
                        error -> assertThat(error.errorCode())
                                .isEqualTo(AgentErrorCode.INVALID_SNAPSHOT));
        assertThatThrownBy(() -> new AgentRunSnapshot(
                AgentRunSnapshot.CURRENT_VERSION, request.agentRunId(), request.task(),
                request.protocolId(), AgentRunStatus.SUSPENDED, 1, now, now,
                List.of(), Map.of(StandardActionHandlers.PENDING_ACTION, pendingCall,
                        StandardActionHandlers.RECONCILIATION_PENDING, true),
                0, 0, null, "approve", waitingStop))
                .isInstanceOfSatisfying(AgentCoreException.class,
                        error -> assertThat(error.errorCode())
                                .isEqualTo(AgentErrorCode.INVALID_SNAPSHOT));
        assertThatThrownBy(() -> new AgentRunSnapshot(
                AgentRunSnapshot.CURRENT_VERSION, request.agentRunId(), request.task(),
                request.protocolId(), AgentRunStatus.SUSPENDED, 1, now, now,
                List.of(), Map.of(StandardActionHandlers.PENDING_ACTION, pendingBatch,
                        StandardActionHandlers.RECONCILIATION_PENDING, true),
                0, 0, null, "reconcile", reconciliationStop))
                .isInstanceOfSatisfying(AgentCoreException.class,
                        error -> assertThat(error.errorCode())
                                .isEqualTo(AgentErrorCode.INVALID_SNAPSHOT));
        AgentObservation unknown = AgentObservation.toolResult(
                "first", null, "write", ToolExecutionResult.failure(
                        AgentErrorCode.TOOL_RESULT_UNKNOWN, "result unknown"));
        AgentStopDecision fatalStop = AgentStopDecision.stop(
                AgentStopReason.FATAL_ERROR, AgentRunStatus.FAILED, "checkpoint failed");
        assertThatThrownBy(() -> new AgentRunSnapshot(
                AgentRunSnapshot.CURRENT_VERSION, request.agentRunId(), request.task(),
                request.protocolId(), AgentRunStatus.FAILED, 1, now, now,
                List.of(unknown), Map.of(StandardActionHandlers.PENDING_ACTION, pendingCall),
                0, 0, null, null, fatalStop))
                .isInstanceOfSatisfying(AgentCoreException.class,
                        error -> assertThat(error.errorCode())
                                .isEqualTo(AgentErrorCode.INVALID_SNAPSHOT));
    }

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
                    assertThat(context.idempotencyKey().agentRunId()).isEqualTo("flow-run");
                    assertThat(context.idempotencyKey().callId()).isEqualTo("inspect#1");
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
        assertLifecycleTrace(listener.events(), "flow-run");
    }

    @Test
    void correctsProtocolErrorThenCompletes() {
        AgentTestSupport.ScriptedModel model = new AgentTestSupport.ScriptedModel(
                "{\"type\":\"final_answer\",\"answer\":\"missing markers\"}",
                "corrected answer");
        AgentTestSupport.RecordingListener listener = new AgentTestSupport.RecordingListener();
        List<AgentLifecycleContext> events = new CopyOnWriteArrayList<>();
        AgentLoopKernel kernel = AgentLoopKernel.builder(model).listener(listener)
                .listener(recordEvents(events)).build();

        AgentResult result = kernel.run(request("protocol-correction"));

        assertThat(result.runStatus()).isEqualTo(AgentRunStatus.COMPLETED);
        assertThat(result.answer()).isEqualTo("corrected answer");
        assertThat(result.snapshot().turn()).isEqualTo(2);
        assertThat(listener.protocolErrors()).isEqualTo(1);
        assertThat(event(events, AgentLifecycleEventType.PROTOCOL_ERROR).stepId())
                .isEqualTo(event(events, AgentLifecycleEventType.DECISION_STARTED).stepId());
        assertLifecycleTrace(events, "protocol-correction");
    }

    @Test
    void recoversAfterModelControlCharacterProtocolError() {
        String invalidJson = envelope("{\"type\":\"final_answer\",\"answer\":\"bad"
                + (char) 0x01 + "text\"}");
        AgentTestSupport.ScriptedModel model = new AgentTestSupport.ScriptedModel(
                invalidJson, "recovered answer");
        AtomicReference<String> protocolErrorCode = new AtomicReference<>();
        AgentLoopKernel kernel = AgentLoopKernel.builder(model)
                .listener(new AgentLifecycleListener() {
                    @Override
                    public void onProtocolError(
                            AgentLifecycleContext context, AgentRequest request,
                            AgentRunState state,
                            com.kset.agent.core.protocol.AgentProtocolException error) {
                        protocolErrorCode.set(error.protocolErrorCode());
                    }
                }).build();

        AgentResult result = kernel.run(request("invalid-model-character"));

        assertThat(result.runStatus()).isEqualTo(AgentRunStatus.COMPLETED);
        assertThat(result.answer()).isEqualTo("recovered answer");
        assertThat(model.calls()).isEqualTo(2);
        assertThat(protocolErrorCode).hasValue(AgentJsonV1ErrorCode.INVALID_JSON.name());
        assertThat(result.snapshot().consecutiveProtocolErrors()).isZero();
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
    void suspendsForConfirmationAndConsumesApprovedIdOnResume() {
        AgentTestSupport.ScriptedModel model = new AgentTestSupport.ScriptedModel(
                confirmation("confirm-1"), confirmation("confirm-1"), "confirmed answer");
        AgentTestSupport.RecordingListener listener = new AgentTestSupport.RecordingListener();
        AgentLoopKernel kernel = AgentLoopKernel.builder(model).listener(listener).build();
        AgentRequest initial = request("confirmation-run");

        AgentResult suspended = kernel.run(initial);

        assertThat(suspended.runStatus()).isEqualTo(AgentRunStatus.SUSPENDED);
        assertThat(suspended.stopDecision().stopReason()).isEqualTo(AgentStopReason.WAITING_INPUT);
        assertThat(suspended.answer()).isNull();
        assertThat(suspended.snapshot().answer()).isNull();
        assertThat(suspended.snapshot().suspensionMessage()).isNotBlank();
        assertThat(suspended.stopDecision().stopMessage())
                .isEqualTo(suspended.snapshot().suspensionMessage());
        assertThat(suspended.snapshot().attributes())
                .containsKey(StandardActionHandlers.PENDING_ACTION);

        AgentRequest approved = request("confirmation-run", initial.options(),
                Map.of(AgentRequest.APPROVED_CONFIRMATION_IDS, List.of("confirm-1")), null);
        AgentResult resumed = kernel.resume(approved, suspended.snapshot());

        assertThat(resumed.runStatus()).isEqualTo(AgentRunStatus.COMPLETED);
        assertThat(resumed.answer()).isEqualTo("confirmed answer");
        assertThat(resumed.snapshot().suspensionMessage()).isNull();
        assertThat(resumed.snapshot().attributes())
                .doesNotContainKey(StandardActionHandlers.PENDING_ACTION);
        assertThat(resumed.snapshot().observations())
                .anyMatch(observation -> StandardActionTypes.CONFIRMATION.equals(
                        observation.actionType()));
        assertThat(listener.byInvocation()).hasSize(2);
        listener.byInvocation().values().forEach(events ->
                assertLifecycleTrace(events, "confirmation-run"));
        assertThat(listener.byInvocation().values())
                .extracting(events -> events.getFirst().invocationType())
                .containsExactlyInAnyOrder(AgentInvocationType.RUN, AgentInvocationType.RESUME);
    }

    @Test
    void requiresConfirmationBeforeResumingTheModelLoop() {
        AgentTestSupport.ScriptedModel model = new AgentTestSupport.ScriptedModel(
                confirmation("confirm-1"), confirmation("confirm-1"), "confirmed answer");
        AgentLoopKernel kernel = AgentLoopKernel.builder(model).build();
        AgentRequest initial = request("missing-confirmation");
        AgentResult suspended = kernel.run(initial);

        assertThatThrownBy(() -> kernel.resume(initial, suspended.snapshot()))
                .isInstanceOfSatisfying(AgentCoreException.class,
                        error -> assertThat(error.errorCode())
                                .isEqualTo(AgentErrorCode.INVALID_RESUME_INPUT));
        assertThat(model.calls()).isEqualTo(1);

        AgentRequest wrongApprovalType = request("missing-confirmation", initial.options(),
                Map.of(AgentRequest.APPROVED_TOOL_CALL_IDS, List.of("confirm-1")), null);
        assertThatThrownBy(() -> kernel.resume(wrongApprovalType, suspended.snapshot()))
                .isInstanceOfSatisfying(AgentCoreException.class,
                        error -> assertThat(error.errorCode())
                                .isEqualTo(AgentErrorCode.INVALID_RESUME_INPUT));
        assertThat(model.calls()).isEqualTo(1);

        AgentRequest approved = request("missing-confirmation", initial.options(),
                Map.of(AgentRequest.APPROVED_CONFIRMATION_IDS, List.of("confirm-1")), null);
        assertThat(kernel.resume(approved, suspended.snapshot()).answer())
                .isEqualTo("confirmed answer");
    }

    @Test
    void rejectsDifferentDecisionUntilPendingConfirmationIsConsumed() {
        AgentTestSupport.ScriptedModel model = new AgentTestSupport.ScriptedModel(
                confirmation("confirm-1"), "premature answer",
                confirmation("confirm-1"), "confirmed answer");
        AgentTestSupport.RecordingListener listener = new AgentTestSupport.RecordingListener();
        AgentLoopKernel kernel = AgentLoopKernel.builder(model).listener(listener).build();
        AgentRequest initial = request("changed-confirmation-action");
        AgentResult suspended = kernel.run(initial);
        AgentRequest approved = request("changed-confirmation-action", initial.options(),
                Map.of(AgentRequest.APPROVED_CONFIRMATION_IDS, List.of("confirm-1")), null);

        AgentResult resumed = kernel.resume(approved, suspended.snapshot());

        assertThat(resumed.runStatus()).isEqualTo(AgentRunStatus.COMPLETED);
        assertThat(resumed.answer()).isEqualTo("confirmed answer");
        assertThat(listener.protocolErrors()).isEqualTo(1);
        assertThat(model.calls()).isEqualTo(4);
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
        AgentTestSupport.RecordingListener listener = new AgentTestSupport.RecordingListener();
        AgentLoopKernel kernel = AgentLoopKernel.builder(model).tools(tools)
                .listener(listener).build();
        AgentRequest request = request("reconcile-run");

        AgentResult suspended = kernel.run(request);

        assertThat(suspended.runStatus()).isEqualTo(AgentRunStatus.SUSPENDED);
        assertThat(suspended.stopDecision().stopReason())
                .isEqualTo(AgentStopReason.RECONCILIATION_REQUIRED);
        assertThat(suspended.answer()).isNull();
        assertThat(suspended.stopDecision().stopMessage())
                .isEqualTo(suspended.snapshot().suspensionMessage());
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
        assertThat(resumed.snapshot().suspensionMessage()).isNull();
        assertThat(resumed.snapshot().attributes())
                .doesNotContainKey(StandardActionHandlers.PENDING_ACTION);
        assertThat(resumed.snapshot().observations().getLast().output()).isEqualTo("saved");
        assertThat(model.calls()).isEqualTo(3);
        assertThat(listener.byInvocation()).hasSize(2);
        listener.byInvocation().values().forEach(events ->
                assertLifecycleTrace(events, "reconcile-run"));
    }

    @Test
    void rejectsNonStringReconciliationIdentity() {
        AgentRequest request = request("numeric-reconciliation-id");
        Instant now = Instant.parse("2026-10-10T00:00:00Z");
        Map<String, Object> pendingCall = Map.of(
                "type", StandardActionTypes.TOOL_CALL,
                "callId", "7", "toolName", "writer", "arguments", Map.of());
        AgentRunSnapshot snapshot = new AgentRunSnapshot(
                AgentRunSnapshot.CURRENT_VERSION, request.agentRunId(), request.task(),
                request.protocolId(), AgentRunStatus.SUSPENDED, 1, now, now,
                List.of(AgentObservation.toolResult("7", null, "writer",
                        ToolExecutionResult.failure(AgentErrorCode.TOOL_RESULT_UNKNOWN,
                                "result unknown"))),
                Map.of(StandardActionHandlers.PENDING_ACTION, pendingCall,
                        StandardActionHandlers.RECONCILIATION_PENDING, true),
                0, 0, null, "reconcile",
                AgentStopDecision.stop(AgentStopReason.RECONCILIATION_REQUIRED,
                        AgentRunStatus.SUSPENDED, "reconcile"));
        AgentObservation invalid = new AgentObservation(
                StandardActionTypes.TOOL_CALL, true, true, "saved", null, null,
                Map.of(AgentObservation.TOOL_CALL_ID, 7,
                        AgentObservation.TOOL_NAME, "writer"));
        AgentLoopKernel kernel = AgentLoopKernel.builder((modelRequest, context) ->
                ModelResponse.text("unused")).build();

        assertThatThrownBy(() -> kernel.resume(
                request, snapshot, new AgentResumeInput(List.of(invalid))))
                .isInstanceOfSatisfying(AgentCoreException.class,
                        error -> assertThat(error.errorCode())
                                .isEqualTo(AgentErrorCode.PENDING_ACTION_MISMATCH));

        AgentObservation invalidTaskId = new AgentObservation(
                StandardActionTypes.TOOL_CALL, true, true, "saved", null, null,
                Map.of(AgentObservation.TOOL_CALL_ID, "7",
                        AgentObservation.TOOL_TASK_ID, 9,
                        AgentObservation.TOOL_NAME, "writer"));
        assertThatThrownBy(() -> kernel.resume(
                request, snapshot, new AgentResumeInput(List.of(invalidTaskId))))
                .isInstanceOfSatisfying(AgentCoreException.class,
                        error -> assertThat(error.errorCode())
                                .isEqualTo(AgentErrorCode.PENDING_ACTION_MISMATCH));
    }

    @Test
    void requiresReconciliationAfterUnknownBatchCheckpointFailure() {
        AgentTestSupport.ScriptedModel model = new AgentTestSupport.ScriptedModel(
                envelope("""
                        {"type":"task_plan","plan":"read","tasks":[
                          {"taskId":"read","title":"Read","dependsOn":[]}
                        ]}
                        """),
                envelope("""
                        {"type":"tool_batch","plan":"read both","toolCalls":[
                          {"callId":"read#1","taskId":"read","toolName":"first","arguments":{}},
                          {"callId":"read#2","taskId":"read","toolName":"second","arguments":{}}
                        ]}
                        """),
                "reconciled answer");
        InMemoryAgentToolRegistry tools = new InMemoryAgentToolRegistry()
                .register(tool("first", (arguments, context) -> ToolExecutionResult.failure(
                        AgentErrorCode.TOOL_RESULT_UNKNOWN, "remote result unknown")))
                .register(tool("second", (arguments, context) -> ToolExecutionResult.success("two")));
        AtomicReference<AgentRunSnapshot> saved = new AtomicReference<>();
        AgentLoopKernel kernel = AgentLoopKernel.builder(model).tools(tools)
                .checkpoints((request, snapshot) -> {
                    if (snapshot.stopDecision() != null && snapshot.stopDecision().stopReason()
                            == AgentStopReason.RECONCILIATION_REQUIRED) {
                        throw new IllegalStateException("checkpoint unavailable");
                    }
                    saved.set(snapshot);
                }).build();
        AgentRequest request = request("failed-reconciliation-checkpoint");

        AgentResult failed = kernel.run(request);

        assertThat(failed.runStatus()).isEqualTo(AgentRunStatus.FAILED);
        assertThat(failed.failure().errorCode()).isEqualTo(AgentErrorCode.CHECKPOINT_FAILED);
        assertThat(saved.get()).isEqualTo(failed.snapshot());
        assertThat(failed.snapshot().attributes())
                .containsEntry(StandardActionHandlers.RECONCILIATION_PENDING, true)
                .containsKey(StandardActionHandlers.PENDING_ACTION);
        assertThatThrownBy(() -> kernel.resume(request, failed.snapshot()))
                .isInstanceOfSatisfying(AgentCoreException.class,
                        error -> assertThat(error.errorCode())
                                .isEqualTo(AgentErrorCode.INVALID_RESUME_INPUT));
        assertThat(model.calls()).isEqualTo(2);

        AgentResult resumed = kernel.resume(request, failed.snapshot(), new AgentResumeInput(
                List.of(
                        AgentObservation.toolResult("read#1", "read", "first",
                                ToolExecutionResult.success("one")),
                        AgentObservation.toolResult("read#2", "read", "second",
                                ToolExecutionResult.success("two")))));

        assertThat(resumed.runStatus()).isEqualTo(AgentRunStatus.COMPLETED);
        assertThat(resumed.answer()).isEqualTo("reconciled answer");
        assertThat(resumed.snapshot().attributes())
                .doesNotContainKeys(StandardActionHandlers.PENDING_ACTION,
                        StandardActionHandlers.RECONCILIATION_PENDING);
    }

    @Test
    void recordsToolFailureAndContinuesToFinalAnswer() {
        AgentTestSupport.ScriptedModel model = new AgentTestSupport.ScriptedModel(
                envelope("""
                        {"type":"task_plan","plan":"read","tasks":[
                          {"taskId":"read","title":"Read","dependsOn":[]}
                        ]}
                        """),
                toolCall("read#1", "read", "reader", "x"),
                "handled tool failure");
        AgentTestSupport.RecordingListener listener = new AgentTestSupport.RecordingListener();
        AgentLoopKernel kernel = AgentLoopKernel.builder(model)
                .tools(new InMemoryAgentToolRegistry().register(tool(
                        "reader", (arguments, context) -> {
                            throw new IllegalStateException("backend unavailable");
                        })))
                .listener(listener).build();

        AgentResult result = kernel.run(request("tool-failure"));

        assertThat(result.runStatus()).isEqualTo(AgentRunStatus.COMPLETED);
        assertThat(result.answer()).isEqualTo("handled tool failure");
        assertThat(result.snapshot().observations().getLast().errorCode())
                .isEqualTo(AgentErrorCode.TOOL_EXECUTION_FAILED.name());
        assertThat(result.snapshot().observations().getLast().attributes())
                .containsEntry(AgentObservation.TOOL_CALL_ID, "read#1")
                .containsEntry(AgentObservation.TOOL_NAME, "reader");
        assertThat(result.snapshot().attributes())
                .doesNotContainKey(StandardActionHandlers.PENDING_ACTION);
        assertThat(model.calls()).isEqualTo(3);
        assertLifecycleTrace(listener.events(), "tool-failure");
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
        List<AgentLifecycleContext> events = new CopyOnWriteArrayList<>();
        InMemoryAgentToolRegistry tools = new InMemoryAgentToolRegistry()
                .register(tool("reader", (arguments, context) -> {
                    toolCalls.incrementAndGet();
                    return ToolExecutionResult.success("unused");
                }));
        AgentLoopKernel kernel = AgentLoopKernel.builder(model).tools(tools)
                .listener(recordEvents(events)).build();

        AgentResult result = kernel.run(request(
                "capacity", options(10, 3, 3, 8, 2), Map.of(), null));

        assertThat(result.runStatus()).isEqualTo(AgentRunStatus.FAILED);
        assertThat(result.stopDecision().stopReason()).isEqualTo(AgentStopReason.CAPACITY_LIMIT);
        assertThat(toolCalls).hasValue(0);
        assertThat(events).extracting(AgentLifecycleContext::eventType)
                .contains(AgentLifecycleEventType.DECISION_FAILED);
        assertLifecycleTrace(events, "capacity");
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
        assertLifecycleTrace(listener.events(), "timeout-run");
    }

    @Test
    void retriesExplicitlyRetryableModelFailureWithinOneModelStep() {
        AtomicInteger modelCalls = new AtomicInteger();
        Set<String> modelStepIds = new java.util.HashSet<>();
        AgentTestSupport.RecordingListener listener = new AgentTestSupport.RecordingListener();
        AgentLoopKernel kernel = AgentLoopKernel.builder((request, context) -> {
            modelStepIds.add(context.stepId());
            if (modelCalls.incrementAndGet() < 3) {
                throw retryableModelFailure();
            }
            return ModelResponse.text("recovered answer");
        }).listener(listener).build();

        AgentResult result = kernel.run(request("model-retry-success"));

        assertThat(result.runStatus()).isEqualTo(AgentRunStatus.COMPLETED);
        assertThat(result.answer()).isEqualTo("recovered answer");
        assertThat(modelCalls).hasValue(3);
        assertThat(modelStepIds).hasSize(1);
        assertThat(listener.events()).extracting(AgentLifecycleContext::eventType)
                .filteredOn(type -> type == AgentLifecycleEventType.MODEL_STARTED)
                .hasSize(1);
        assertLifecycleTrace(listener.events(), "model-retry-success");
    }

    @Test
    void stopsAfterRetryableModelFailureExhaustsAttempts() {
        AtomicInteger modelCalls = new AtomicInteger();
        AgentTestSupport.RecordingListener listener = new AgentTestSupport.RecordingListener();
        AgentLoopKernel kernel = AgentLoopKernel.builder((request, context) -> {
            modelCalls.incrementAndGet();
            throw retryableModelFailure();
        }).modelRetry(new AgentModelRetryOptions(
                3, Duration.ofMillis(1), Duration.ofMillis(2)))
                .listener(listener).build();

        AgentResult result = kernel.run(request("model-retry-exhausted"));

        assertThat(result.runStatus()).isEqualTo(AgentRunStatus.FAILED);
        assertThat(result.failure().errorCode()).isEqualTo(AgentErrorCode.MODEL_CALL_FAILED);
        assertThat(result.failure().retryable()).isTrue();
        assertThat(modelCalls).hasValue(3);
        assertThat(listener.events()).extracting(AgentLifecycleContext::eventType)
                .contains(AgentLifecycleEventType.MODEL_FAILED);
        assertLifecycleTrace(listener.events(), "model-retry-exhausted");
    }

    @Test
    void doesNotRetryOrdinaryModelException() {
        AtomicInteger modelCalls = new AtomicInteger();
        AgentLoopKernel kernel = AgentLoopKernel.builder((request, context) -> {
            modelCalls.incrementAndGet();
            throw new IllegalStateException("provider rejected request");
        }).build();

        AgentResult result = kernel.run(request("model-not-retryable"));

        assertThat(result.failure().errorCode()).isEqualTo(AgentErrorCode.MODEL_CALL_FAILED);
        assertThat(result.failure().retryable()).isFalse();
        assertThat(modelCalls).hasValue(1);
    }

    @Test
    void doesNotRetryDifferentErrorCodeEvenWhenMarkedRetryable() {
        AtomicInteger modelCalls = new AtomicInteger();
        AgentLoopKernel kernel = AgentLoopKernel.builder((request, context) -> {
            modelCalls.incrementAndGet();
            throw new AgentCoreException(AgentErrorCode.EXTENSION_CONTRACT_VIOLATION,
                    "invalid model adapter response", true, null);
        }).build();

        AgentResult result = kernel.run(request("model-wrong-retry-code"));

        assertThat(result.failure().errorCode())
                .isEqualTo(AgentErrorCode.EXTENSION_CONTRACT_VIOLATION);
        assertThat(modelCalls).hasValue(1);
    }

    @Test
    void stopsRetryingWhenLaterModelFailureIsNotRetryable() {
        AtomicInteger modelCalls = new AtomicInteger();
        AgentLoopKernel kernel = AgentLoopKernel.builder((request, context) -> {
            if (modelCalls.incrementAndGet() == 1) {
                throw retryableModelFailure();
            }
            throw new AgentCoreException(AgentErrorCode.MODEL_CALL_FAILED,
                    "provider rejected request");
        }).modelRetry(new AgentModelRetryOptions(
                3, Duration.ofMillis(1), Duration.ofMillis(1))).build();

        AgentResult result = kernel.run(request("model-retry-then-reject"));

        assertThat(result.failure().errorCode()).isEqualTo(AgentErrorCode.MODEL_CALL_FAILED);
        assertThat(result.failure().retryable()).isFalse();
        assertThat(modelCalls).hasValue(2);
    }

    @Test
    void cancellationPreventsNextModelAttempt() {
        AtomicBoolean cancelled = new AtomicBoolean();
        AtomicInteger modelCalls = new AtomicInteger();
        AgentLoopKernel kernel = AgentLoopKernel.builder((request, context) -> {
            modelCalls.incrementAndGet();
            cancelled.set(true);
            throw retryableModelFailure();
        }).build();

        AgentResult result = kernel.run(request(
                "model-retry-cancelled", AgentLoopOptions.defaults(),
                Map.of(), cancelled::get));

        assertThat(result.runStatus()).isEqualTo(AgentRunStatus.CANCELLED);
        assertThat(result.stopDecision().stopReason()).isEqualTo(AgentStopReason.USER_CANCELLED);
        assertThat(modelCalls).hasValue(1);
    }

    @Test
    void interruptionPreventsNextModelAttempt() {
        AtomicInteger modelCalls = new AtomicInteger();
        AgentLoopKernel kernel = AgentLoopKernel.builder((request, context) -> {
            modelCalls.incrementAndGet();
            Thread.currentThread().interrupt();
            throw retryableModelFailure();
        }).build();

        AgentResult result;
        try {
            result = kernel.run(request("model-retry-interrupted"));
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }

        assertThat(result.runStatus()).isEqualTo(AgentRunStatus.CANCELLED);
        assertThat(result.stopDecision().stopReason())
                .isEqualTo(AgentStopReason.THREAD_INTERRUPTED);
        assertThat(modelCalls).hasValue(1);
    }

    @Test
    void doesNotRetryModelFailureAfterDeadline() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-10T00:00:00Z"));
        AtomicInteger modelCalls = new AtomicInteger();
        AgentLoopKernel kernel = AgentLoopKernel.builder((request, context) -> {
            modelCalls.incrementAndGet();
            clock.advance(Duration.ofSeconds(2));
            throw retryableModelFailure();
        }).clock(clock).build();
        AgentLoopOptions options = new AgentLoopOptions(
                10, Duration.ofSeconds(1), 3, 3, 256,
                Duration.ofSeconds(1), Duration.ofSeconds(1), 8, 8);

        AgentResult result = kernel.run(request(
                "model-retry-deadline", options, Map.of(), null));

        assertThat(result.stopDecision().stopReason()).isEqualTo(AgentStopReason.FATAL_ERROR);
        assertThat(result.failure().errorCode()).isEqualTo(AgentErrorCode.MODEL_CALL_FAILED);
        assertThat(modelCalls).hasValue(1);
    }

    @Test
    void maxAttemptsOneDisablesModelRetry() {
        AtomicInteger modelCalls = new AtomicInteger();
        AgentLoopKernel kernel = AgentLoopKernel.builder((request, context) -> {
            modelCalls.incrementAndGet();
            throw retryableModelFailure();
        }).modelRetry(new AgentModelRetryOptions(
                1, Duration.ofMillis(1), Duration.ofMillis(1))).build();

        AgentResult result = kernel.run(request("model-retry-disabled"));

        assertThat(result.failure().errorCode()).isEqualTo(AgentErrorCode.MODEL_CALL_FAILED);
        assertThat(modelCalls).hasValue(1);
    }

    @Test
    void rejectsInvalidModelRetryOptions() {
        assertThatThrownBy(() -> new AgentModelRetryOptions(
                0, Duration.ofMillis(1), Duration.ofSeconds(1)))
                .isInstanceOfSatisfying(AgentCoreException.class, error ->
                        assertThat(error.errorCode())
                                .isEqualTo(AgentErrorCode.INVALID_CONFIGURATION));
        assertThatThrownBy(() -> new AgentModelRetryOptions(
                3, Duration.ofSeconds(2), Duration.ofSeconds(1)))
                .isInstanceOfSatisfying(AgentCoreException.class, error ->
                        assertThat(error.errorCode())
                                .isEqualTo(AgentErrorCode.INVALID_CONFIGURATION));
    }

    @Test
    void recordsModelLatencyWhenResponseArrivesBeforeDeadline() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-09T00:00:00Z"));
        List<AgentLifecycleContext> events = new CopyOnWriteArrayList<>();
        AgentLoopKernel kernel = AgentLoopKernel.builder((request, context) -> {
            assertThat(context.deadline()).isEqualTo(clock.instant().plusSeconds(1));
            clock.advance(Duration.ofMillis(750));
            return ModelResponse.text("answer within deadline");
        }).clock(clock).listener(recordEvents(events)).build();
        AgentLoopOptions options = new AgentLoopOptions(
                10, Duration.ofSeconds(1), 3, 3, 256,
                Duration.ofSeconds(1), Duration.ofSeconds(1), 8, 8);

        AgentResult result = kernel.run(request("model-latency", options, Map.of(), null));

        assertThat(result.runStatus()).isEqualTo(AgentRunStatus.COMPLETED);
        assertThat(result.answer()).isEqualTo("answer within deadline");
        assertThat(result.failure()).isNull();
        assertThat(event(events, AgentLifecycleEventType.MODEL_COMPLETED).elapsed())
                .isEqualTo(Duration.ofMillis(750));
    }

    @Test
    void timesOutWhenModelReturnsExactlyAtDeadline() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-09T00:00:00Z"));
        List<AgentLifecycleContext> events = new CopyOnWriteArrayList<>();
        AtomicInteger modelCalls = new AtomicInteger();
        AgentLoopKernel kernel = AgentLoopKernel.builder((request, context) -> {
            modelCalls.incrementAndGet();
            clock.advance(Duration.ofSeconds(1));
            return ModelResponse.text("answer at deadline");
        }).clock(clock).listener(recordEvents(events)).build();
        AgentLoopOptions options = new AgentLoopOptions(
                10, Duration.ofSeconds(1), 3, 3, 256,
                Duration.ofSeconds(1), Duration.ofSeconds(1), 8, 8);

        AgentResult result = kernel.run(request("model-deadline-boundary", options,
                Map.of(), null));

        assertThat(result.runStatus()).isEqualTo(AgentRunStatus.FAILED);
        assertThat(result.stopDecision().stopReason()).isEqualTo(AgentStopReason.TIMEOUT);
        assertThat(result.failure()).isNull();
        assertThat(result.answer()).isNull();
        assertThat(modelCalls).hasValue(1);
        assertThat(event(events, AgentLifecycleEventType.MODEL_COMPLETED).elapsed())
                .isEqualTo(Duration.ofSeconds(1));
        assertThat(events).extracting(AgentLifecycleContext::eventType)
                .doesNotContain(AgentLifecycleEventType.DECISION_STARTED);
        assertLifecycleTrace(events, "model-deadline-boundary");
    }

    @Test
    void keepsModelFailureWhenDeadlineAlsoExpires() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-09T00:00:00Z"));
        List<AgentLifecycleContext> events = new CopyOnWriteArrayList<>();
        AgentLoopKernel kernel = AgentLoopKernel.builder((request, context) -> {
            clock.advance(Duration.ofSeconds(2));
            throw new IllegalStateException("provider unavailable");
        }).clock(clock).listener(recordEvents(events)).build();
        AgentLoopOptions options = new AgentLoopOptions(
                10, Duration.ofSeconds(1), 3, 3, 256,
                Duration.ofSeconds(1), Duration.ofSeconds(1), 8, 8);

        AgentResult result = kernel.run(request("model-error-after-deadline", options,
                Map.of(), null));

        assertThat(result.stopDecision().stopReason()).isEqualTo(AgentStopReason.FATAL_ERROR);
        assertThat(result.failure().errorCode()).isEqualTo(AgentErrorCode.MODEL_CALL_FAILED);
        assertThat(event(events, AgentLifecycleEventType.MODEL_FAILED).stepId())
                .isEqualTo(event(events, AgentLifecycleEventType.MODEL_STARTED).stepId());
        assertThat(event(events, AgentLifecycleEventType.TURN_FAILED).stepId())
                .isEqualTo(event(events, AgentLifecycleEventType.TURN_STARTED).stepId());
        assertThat(event(events, AgentLifecycleEventType.RUN_FAILED).stepId())
                .isEqualTo(event(events, AgentLifecycleEventType.RUN_STARTED).stepId());
        assertLifecycleTrace(events, "model-error-after-deadline");
    }

    @Test
    void reportsStableModelFailureForExceptionWithoutMessage() {
        AgentLoopKernel kernel = AgentLoopKernel.builder((request, context) -> {
            throw new IllegalStateException();
        }).build();

        AgentResult result = kernel.run(request("model-empty-error-message"));

        assertThat(result.runStatus()).isEqualTo(AgentRunStatus.FAILED);
        assertThat(result.stopDecision().stopReason()).isEqualTo(AgentStopReason.FATAL_ERROR);
        assertThat(result.failure().errorCode()).isEqualTo(AgentErrorCode.MODEL_CALL_FAILED);
        assertThat(result.failure().errorMessage()).contains("IllegalStateException");
    }

    @Test
    void closesStartedStepsWhenDeadlineExpiresBeforeModelCall() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-09T00:00:00Z"));
        List<AgentLifecycleContext> events = new CopyOnWriteArrayList<>();
        AtomicInteger modelCalls = new AtomicInteger();
        AgentLifecycleListener listener = new AgentLifecycleListener() {
            @Override
            public void onEvent(AgentLifecycleContext context) {
                events.add(context);
            }

            @Override
            public void beforeModel(
                    AgentLifecycleContext context, AgentRequest request,
                    AgentRunState state, ModelRequest modelRequest) {
                clock.advance(Duration.ofSeconds(2));
            }
        };
        AgentLoopKernel kernel = AgentLoopKernel.builder((request, context) -> {
            modelCalls.incrementAndGet();
            return ModelResponse.text("unused");
        }).clock(clock).listener(listener).build();
        AgentLoopOptions options = new AgentLoopOptions(
                10, Duration.ofSeconds(1), 3, 3, 256,
                Duration.ofSeconds(1), Duration.ofSeconds(1), 8, 8);

        AgentResult result = kernel.run(request("stop-before-model", options, Map.of(), null));

        assertThat(result.stopDecision().stopReason()).isEqualTo(AgentStopReason.TIMEOUT);
        assertThat(modelCalls).hasValue(0);
        assertThat(event(events, AgentLifecycleEventType.MODEL_FAILED).stepId())
                .isEqualTo(event(events, AgentLifecycleEventType.MODEL_STARTED).stepId());
        assertThat(event(events, AgentLifecycleEventType.TURN_FAILED).stepId())
                .isEqualTo(event(events, AgentLifecycleEventType.TURN_STARTED).stepId());
        assertThat(event(events, AgentLifecycleEventType.RUN_STOPPED).stepId())
                .isEqualTo(event(events, AgentLifecycleEventType.RUN_STARTED).stepId());
        assertLifecycleTrace(events, "stop-before-model");
    }

    @Test
    void completesTurnBeforeReturningFinalAnswer() {
        List<AgentLifecycleContext> events = new CopyOnWriteArrayList<>();
        AgentLoopKernel kernel = AgentLoopKernel.builder(new AgentTestSupport.ScriptedModel("done"))
                .listener(recordEvents(events)).build();

        AgentResult result = kernel.run(request("final-answer-steps"));

        assertThat(result.runStatus()).isEqualTo(AgentRunStatus.COMPLETED);
        assertThat(event(events, AgentLifecycleEventType.TURN_COMPLETED).stepId())
                .isEqualTo(event(events, AgentLifecycleEventType.TURN_STARTED).stepId());
        assertThat(event(events, AgentLifecycleEventType.RUN_STOPPED).stepId())
                .isEqualTo(event(events, AgentLifecycleEventType.RUN_STARTED).stepId());
        assertThat(events).extracting(AgentLifecycleContext::eventType)
                .doesNotContain(AgentLifecycleEventType.TURN_FAILED);
        assertLifecycleTrace(events, "final-answer-steps");
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
        AgentTestSupport.RecordingListener listener = new AgentTestSupport.RecordingListener();
        AgentLoopKernel kernel = AgentLoopKernel.builder((request, context) -> {
            modelCalls.incrementAndGet();
            return com.kset.agent.core.model.ModelResponse.text("unused");
        }).checkpoints((request, snapshot) -> {
            throw new IllegalStateException("store unavailable");
        }).listener(listener).build();

        AgentResult result = kernel.run(request("checkpoint-failure"));

        assertThat(result.runStatus()).isEqualTo(AgentRunStatus.FAILED);
        assertThat(result.stopDecision().stopReason()).isEqualTo(AgentStopReason.FATAL_ERROR);
        assertThat(result.failure()).isNotNull();
        assertThat(result.failure().errorCode()).isEqualTo(AgentErrorCode.CHECKPOINT_FAILED);
        assertThat(modelCalls).hasValue(0);
        assertLifecycleTrace(listener.events(), "checkpoint-failure");
    }

    @Test
    void reportsFailureCheckpointErrorInsteadOfOnlyTheOriginalFailure() {
        List<AgentLifecycleContext> events = new CopyOnWriteArrayList<>();
        AgentLoopKernel kernel = AgentLoopKernel.builder((request, context) -> {
            throw new IllegalStateException("provider unavailable");
        }).checkpoints((request, snapshot) -> {
            if (snapshot.runStatus() == AgentRunStatus.FAILED) {
                throw new IllegalStateException("store unavailable");
            }
        }).listener(recordEvents(events)).build();

        AgentResult result = kernel.run(request("failure-checkpoint-error"));

        assertThat(result.failure().errorCode()).isEqualTo(AgentErrorCode.CHECKPOINT_FAILED);
        assertThat(result.stopDecision().stopReason()).isEqualTo(AgentStopReason.FATAL_ERROR);
        assertThat(events).extracting(AgentLifecycleContext::eventType)
                .contains(AgentLifecycleEventType.CHECKPOINT_FAILED);
        assertThat(event(events, AgentLifecycleEventType.RUN_FAILED).stepId())
                .isEqualTo(event(events, AgentLifecycleEventType.RUN_STARTED).stepId());
        assertLifecycleTrace(events, "failure-checkpoint-error");
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
        assertThatThrownBy(() -> kernel.resume(initial, suspended.snapshot()))
                .isInstanceOfSatisfying(AgentCoreException.class,
                        error -> assertThat(error.errorCode())
                                .isEqualTo(AgentErrorCode.INVALID_RESUME_INPUT));
        assertThat(toolCalls).hasValue(0);
        assertThat(model.calls()).isEqualTo(2);
        AgentRequest wrongApprovalType = request("tool-confirmation", initial.options(),
                Map.of(AgentRequest.APPROVED_CONFIRMATION_IDS, List.of("write#1")), null);
        assertThatThrownBy(() -> kernel.resume(wrongApprovalType, suspended.snapshot()))
                .isInstanceOfSatisfying(AgentCoreException.class,
                        error -> assertThat(error.errorCode())
                                .isEqualTo(AgentErrorCode.INVALID_RESUME_INPUT));
        assertThat(model.calls()).isEqualTo(2);
        AgentRequest approved = request("tool-confirmation", initial.options(),
                Map.of(AgentRequest.APPROVED_TOOL_CALL_IDS, List.of("write#1")), null);
        AgentResult resumed = kernel.resume(approved, suspended.snapshot());

        assertThat(suspended.stopDecision().stopReason()).isEqualTo(AgentStopReason.WAITING_INPUT);
        assertThat(resumed.runStatus()).isEqualTo(AgentRunStatus.COMPLETED);
        assertThat(resumed.answer()).isEqualTo("written");
        assertThat(toolCalls).hasValue(1);
        assertThat(resumed.snapshot().attributes())
                .containsKey(StandardActionHandlers.APPROVED_TOOL_OPERATIONS);
    }

    @Test
    void confirmsToolBatchCallsOneAtATimeBeforeExecution() {
        String batch = envelope("""
                {"type":"tool_batch","plan":"write both","toolCalls":[
                  {"callId":"write#1","taskId":"write","toolName":"first","arguments":{}},
                  {"callId":"write#2","taskId":"write","toolName":"second","arguments":{}}
                ]}
                """);
        AgentTestSupport.ScriptedModel model = new AgentTestSupport.ScriptedModel(
                envelope("""
                        {"type":"task_plan","plan":"write","tasks":[
                          {"taskId":"write","title":"Write","dependsOn":[]}
                        ]}
                        """),
                batch, batch, batch, "both written");
        AtomicInteger toolCalls = new AtomicInteger();
        InMemoryAgentToolRegistry tools = new InMemoryAgentToolRegistry()
                .register(dangerousTool("first", (arguments, context) -> {
                    toolCalls.incrementAndGet();
                    return ToolExecutionResult.success("first done");
                }))
                .register(dangerousTool("second", (arguments, context) -> {
                    toolCalls.incrementAndGet();
                    return ToolExecutionResult.success("second done");
                }));
        AgentLoopKernel kernel = AgentLoopKernel.builder(model).tools(tools).build();
        AgentRequest initial = request("batch-confirmation");

        AgentResult firstWait = kernel.run(initial);
        AgentRequest confirmFirst = request("batch-confirmation", initial.options(),
                Map.of(AgentRequest.APPROVED_TOOL_CALL_IDS, List.of("write#1")), null);
        AgentResult secondWait = kernel.resume(confirmFirst, firstWait.snapshot());
        AgentRequest confirmSecond = request("batch-confirmation", initial.options(),
                Map.of(AgentRequest.APPROVED_TOOL_CALL_IDS, List.of("write#2")), null);
        AgentResult completed = kernel.resume(confirmSecond, secondWait.snapshot());

        assertThat(firstWait.runStatus()).isEqualTo(AgentRunStatus.SUSPENDED);
        assertThat(secondWait.runStatus()).isEqualTo(AgentRunStatus.SUSPENDED);
        assertThat(completed.runStatus()).isEqualTo(AgentRunStatus.COMPLETED);
        assertThat(completed.answer()).isEqualTo("both written");
        assertThat(toolCalls).hasValue(2);
    }

    @Test
    void keepsTheWholeBatchPendingWhenTheApprovedCallIsLast() {
        String batch = envelope("""
                {"type":"tool_batch","plan":"write both","toolCalls":[
                  {"callId":"write#2","taskId":"write","toolName":"second","arguments":{}},
                  {"callId":"write#1","taskId":"write","toolName":"first","arguments":{}}
                ]}
                """);
        AgentTestSupport.ScriptedModel model = new AgentTestSupport.ScriptedModel(
                envelope("""
                        {"type":"task_plan","plan":"write","tasks":[
                          {"taskId":"write","title":"Write","dependsOn":[]}
                        ]}
                        """),
                envelope("""
                        {"type":"tool_call","toolCall":{
                          "callId":"write#1","taskId":"write",
                          "toolName":"first","arguments":{}
                        }}
                        """),
                batch,
                envelope("""
                        {"type":"tool_call","toolCall":{
                          "callId":"write#2","taskId":"write",
                          "toolName":"second","arguments":{}
                        }}
                        """),
                batch, "both written");
        AtomicInteger toolCalls = new AtomicInteger();
        InMemoryAgentToolRegistry tools = new InMemoryAgentToolRegistry()
                .register(dangerousTool("first", (arguments, context) -> {
                    toolCalls.incrementAndGet();
                    return ToolExecutionResult.success("first done");
                }))
                .register(dangerousTool("second", (arguments, context) -> {
                    toolCalls.incrementAndGet();
                    return ToolExecutionResult.success("second done");
                }));
        AgentTestSupport.RecordingListener listener = new AgentTestSupport.RecordingListener();
        AgentLoopKernel kernel = AgentLoopKernel.builder(model).tools(tools)
                .listener(listener).build();
        AgentRequest initial = request("reordered-batch-confirmation");

        AgentResult firstWait = kernel.run(initial);
        AgentRequest approveFirst = request("reordered-batch-confirmation", initial.options(),
                Map.of(AgentRequest.APPROVED_TOOL_CALL_IDS, List.of("write#1")), null);
        AgentResult secondWait = kernel.resume(approveFirst, firstWait.snapshot());

        assertThat(secondWait.runStatus()).isEqualTo(AgentRunStatus.SUSPENDED);
        assertThat(secondWait.snapshot().attributes())
                .containsKey(StandardActionHandlers.PENDING_BATCH);
        assertThat(((Map<?, ?>) secondWait.snapshot().attributes()
                .get(StandardActionHandlers.APPROVED_TOOL_OPERATIONS))
                .containsKey("write#1")).isTrue();
        assertThat(toolCalls).hasValue(0);

        AgentRequest approveSecond = request("reordered-batch-confirmation", initial.options(),
                Map.of(AgentRequest.APPROVED_TOOL_CALL_IDS, List.of("write#2")), null);
        AgentResult completed = kernel.resume(approveSecond, secondWait.snapshot());

        assertThat(completed.runStatus()).isEqualTo(AgentRunStatus.COMPLETED);
        assertThat(completed.answer()).isEqualTo("both written");
        assertThat(completed.snapshot().attributes())
                .doesNotContainKeys(StandardActionHandlers.PENDING_ACTION,
                        StandardActionHandlers.PENDING_BATCH);
        assertThat(listener.protocolErrors()).isEqualTo(1);
        assertThat(toolCalls).hasValue(2);
    }

    @Test
    void correctsChangedToolArgumentsBeforeExecution() {
        AgentTestSupport.ScriptedModel model = new AgentTestSupport.ScriptedModel(
                envelope("""
                        {"type":"task_plan","plan":"write","tasks":[
                          {"taskId":"write","title":"Write","dependsOn":[]}
                        ]}
                        """),
                toolCall("write#1", "write", "writer", "x"),
                toolCall("write#1", "write", "writer", "changed"),
                toolCall("write#1", "write", "writer", "x"),
                "operation corrected");
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
        AgentRequest approved = request("changed-confirmation", initial.options(),
                Map.of(AgentRequest.APPROVED_TOOL_CALL_IDS, List.of("write#1")), null);

        AgentResult resumed = kernel.resume(approved, suspended.snapshot());

        assertThat(resumed.runStatus()).isEqualTo(AgentRunStatus.COMPLETED);
        assertThat(resumed.answer()).isEqualTo("operation corrected");
        assertThat(listener.protocolErrors()).isEqualTo(1);
        assertThat(toolCalls).hasValue(1);
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
        List<AgentLifecycleContext> events = new CopyOnWriteArrayList<>();
        AgentLoopKernel kernel = AgentLoopKernel.builder(model)
                .tools(new InMemoryAgentToolRegistry().register(dangerousTool(
                        "writer", (arguments, context) -> {
                            toolCalls.incrementAndGet();
                            return ToolExecutionResult.success("unexpected");
                        })))
                .listener(recordEvents(events))
                .build();

        AgentResult result = kernel.run(request(
                "preconfirmed", AgentLoopOptions.defaults(),
                Map.of(AgentRequest.APPROVED_TOOL_CALL_IDS, List.of("write#1")), null));

        assertThat(result.runStatus()).isEqualTo(AgentRunStatus.FAILED);
        assertThat(result.failure().errorCode()).isEqualTo(AgentErrorCode.PENDING_ACTION_MISMATCH);
        assertThat(toolCalls).hasValue(0);
        assertThat(events).extracting(AgentLifecycleContext::eventType)
                .contains(AgentLifecycleEventType.ACTION_FAILED);
        assertLifecycleTrace(events, "preconfirmed");
    }

    @Test
    void allowsRetryOfTheSameToolOperationDefinition() {
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
                                AgentRunStatus.SUSPENDED, null, "custom wait",
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

    private static AgentLifecycleListener recordEvents(List<AgentLifecycleContext> events) {
        return new AgentLifecycleListener() {
            @Override
            public void onEvent(AgentLifecycleContext context) {
                events.add(context);
            }
        };
    }

    private static AgentCoreException retryableModelFailure() {
        return new AgentCoreException(AgentErrorCode.MODEL_CALL_FAILED,
                "provider temporarily unavailable", true, null);
    }

    private static AgentLifecycleContext event(
            List<AgentLifecycleContext> events, AgentLifecycleEventType eventType) {
        return events.stream()
                .filter(context -> context.eventType() == eventType)
                .findFirst().orElseThrow();
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
