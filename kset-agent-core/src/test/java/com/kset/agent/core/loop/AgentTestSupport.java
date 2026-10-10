package com.kset.agent.core.loop;

import com.kset.agent.core.api.AgentRequest;
import com.kset.agent.core.checkpoint.AgentCheckpointPort;
import com.kset.agent.core.event.AgentLifecycleContext;
import com.kset.agent.core.event.AgentLifecycleEventType;
import com.kset.agent.core.event.AgentLifecycleListener;
import com.kset.agent.core.model.AgentModel;
import com.kset.agent.core.model.ModelRequest;
import com.kset.agent.core.model.ModelResponse;
import com.kset.agent.core.protocol.AgentProtocolException;
import com.kset.agent.core.protocol.json.AgentJsonV1Codec;
import com.kset.agent.core.stop.AgentCancellation;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

final class AgentTestSupport {

    private AgentTestSupport() {
    }

    static String envelope(String json) {
        return AgentJsonV1Codec.START_MARKER + "\n" + json + "\n"
                + AgentJsonV1Codec.END_MARKER;
    }

    static AgentRequest request(String runId) {
        return request(runId, AgentLoopOptions.defaults(), Map.of(), AgentCancellation.none());
    }

    static AgentRequest request(
            String runId, AgentLoopOptions options, Map<String, Object> attributes,
            AgentCancellation cancellation) {
        return new AgentRequest(runId, "test task", AgentJsonV1Codec.ID,
                options, attributes, cancellation);
    }

    static AgentLoopOptions options(
            int maxTurns, int protocolErrorLimit, int noProgressLimit,
            int maxActions, int maxBatchCalls) {
        return new AgentLoopOptions(maxTurns, Duration.ofSeconds(10), protocolErrorLimit,
                noProgressLimit, 256, Duration.ofSeconds(3), Duration.ofSeconds(3),
                maxActions, maxBatchCalls);
    }

    static void assertLifecycleTrace(List<AgentLifecycleContext> events, String runId) {
        assertThat(events).isNotEmpty();
        AgentLifecycleContext runStarted = events.getFirst();
        assertThat(runStarted.eventType()).isEqualTo(AgentLifecycleEventType.RUN_STARTED);
        assertThat(events.getLast().eventType())
                .isEqualTo(AgentLifecycleEventType.RUN_RETURNED);
        Map<String, AgentLifecycleContext> openSteps = new HashMap<>();
        for (int index = 0; index < events.size(); index++) {
            AgentLifecycleContext event = events.get(index);
            assertThat(event.runId()).isEqualTo(runId);
            assertThat(event.invocationId()).isEqualTo(runStarted.invocationId());
            assertThat(event.eventSequence()).isEqualTo(index + 1L);
            assertThat(event.eventId())
                    .isEqualTo(event.invocationId() + ":" + event.eventSequence());
            AgentLifecycleEventType startedType = startedType(event.eventType());
            if (event.eventType() == startedType) {
                if (event.eventType() == AgentLifecycleEventType.RUN_STARTED) {
                    assertThat(event.parentStepId()).isNull();
                } else {
                    assertThat(openSteps).containsKey(event.parentStepId());
                }
                assertThat(openSteps.put(event.stepId(), event)).isNull();
            } else if (startedType != null) {
                AgentLifecycleContext started = openSteps.remove(event.stepId());
                assertThat(started).as("start event for %s", event.eventType()).isNotNull();
                assertThat(started.eventType()).isEqualTo(startedType);
                assertThat(event.parentStepId()).isEqualTo(started.parentStepId());
                assertThat(event.stepType()).isEqualTo(started.stepType());
                assertThat(event.operation()).isEqualTo(started.operation());
            } else if (event.eventType() == AgentLifecycleEventType.RUN_FAILED) {
                assertThat(event.stepId()).isEqualTo(runStarted.stepId());
                assertThat(openSteps).containsKey(runStarted.stepId());
            } else if (event.eventType() == AgentLifecycleEventType.RUN_RETURNED) {
                assertThat(event.stepId()).isEqualTo(runStarted.stepId());
                assertThat(openSteps).isEmpty();
            }
        }
        assertThat(openSteps).isEmpty();
    }

    private static AgentLifecycleEventType startedType(AgentLifecycleEventType eventType) {
        return switch (eventType) {
            case RUN_STARTED, RUN_STOPPED -> AgentLifecycleEventType.RUN_STARTED;
            case TURN_STARTED, TURN_COMPLETED, TURN_FAILED -> AgentLifecycleEventType.TURN_STARTED;
            case MODEL_STARTED, MODEL_COMPLETED, MODEL_FAILED -> AgentLifecycleEventType.MODEL_STARTED;
            case DECISION_STARTED, DECISION_ACCEPTED, DECISION_FAILED, PROTOCOL_ERROR ->
                    AgentLifecycleEventType.DECISION_STARTED;
            case ACTION_STARTED, ACTION_COMPLETED, ACTION_FAILED -> AgentLifecycleEventType.ACTION_STARTED;
            case CHECKPOINT_SAVING, CHECKPOINT_SAVED, CHECKPOINT_FAILED ->
                    AgentLifecycleEventType.CHECKPOINT_SAVING;
            case RUN_FAILED, RUN_RETURNED -> null;
        };
    }

    static final class ScriptedModel implements AgentModel {
        private final ConcurrentLinkedQueue<String> responses;
        private final AtomicInteger calls = new AtomicInteger();

        ScriptedModel(String... responses) {
            this.responses = new ConcurrentLinkedQueue<>(List.of(responses));
        }

        @Override
        public ModelResponse generate(
                ModelRequest request, com.kset.agent.core.execution.AgentExecutionContext context) {
            calls.incrementAndGet();
            String response = responses.poll();
            if (response == null) {
                throw new IllegalStateException("no scripted model response for " + context.runId());
            }
            return ModelResponse.text(response);
        }

        int calls() {
            return calls.get();
        }
    }

    static final class RecordingCheckpoint implements AgentCheckpointPort {
        private final Map<String, CopyOnWriteArrayList<AgentRunSnapshot>> snapshots =
                new ConcurrentHashMap<>();

        @Override
        public void save(AgentRequest request, AgentRunSnapshot snapshot) {
            snapshots.computeIfAbsent(request.runId(), ignored -> new CopyOnWriteArrayList<>())
                    .add(snapshot);
        }

        AgentRunSnapshot latest(String runId) {
            List<AgentRunSnapshot> values = snapshots.getOrDefault(
                    runId, new CopyOnWriteArrayList<>());
            return values.isEmpty() ? null : values.getLast();
        }

        List<AgentRunSnapshot> all(String runId) {
            return List.copyOf(snapshots.getOrDefault(runId, new CopyOnWriteArrayList<>()));
        }
    }

    static final class RecordingListener implements AgentLifecycleListener {
        private final List<AgentLifecycleContext> events = new CopyOnWriteArrayList<>();
        private final AtomicInteger protocolErrors = new AtomicInteger();

        @Override
        public void onEvent(AgentLifecycleContext context) {
            events.add(context);
        }

        @Override
        public void onProtocolError(
                AgentLifecycleContext context, AgentRequest request,
                AgentRunState state, AgentProtocolException error) {
            protocolErrors.incrementAndGet();
        }

        List<AgentLifecycleContext> events() {
            return List.copyOf(events);
        }

        int protocolErrors() {
            return protocolErrors.get();
        }

        Map<String, List<AgentLifecycleContext>> byInvocation() {
            Map<String, List<AgentLifecycleContext>> result = new ConcurrentHashMap<>();
            events.forEach(event -> result.computeIfAbsent(
                    event.invocationId(), ignored -> new ArrayList<>()).add(event));
            return Map.copyOf(result);
        }
    }
}
