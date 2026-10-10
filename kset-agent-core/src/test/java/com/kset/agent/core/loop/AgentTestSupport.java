package com.kset.agent.core.loop;

import com.kset.agent.core.action.AgentAction;
import com.kset.agent.core.action.AgentActionResult;
import com.kset.agent.core.api.AgentRequest;
import com.kset.agent.core.api.AgentResult;
import com.kset.agent.core.checkpoint.AgentCheckpointPort;
import com.kset.agent.core.event.AgentLifecycleContext;
import com.kset.agent.core.event.AgentLifecycleListener;
import com.kset.agent.core.model.AgentModel;
import com.kset.agent.core.model.ModelRequest;
import com.kset.agent.core.model.ModelResponse;
import com.kset.agent.core.protocol.AgentDecision;
import com.kset.agent.core.protocol.AgentProtocolException;
import com.kset.agent.core.protocol.json.AgentJsonV1Codec;
import com.kset.agent.core.stop.AgentCancellation;
import com.kset.agent.core.stop.AgentStopDecision;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

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
        public void beforeRun(
                AgentLifecycleContext context, AgentRequest request, AgentRunState state) {
            events.add(context);
        }

        @Override
        public void beforeTurn(
                AgentLifecycleContext context, AgentRequest request, AgentRunState state) {
            events.add(context);
        }

        @Override
        public void beforeModel(
                AgentLifecycleContext context, AgentRequest request,
                AgentRunState state, ModelRequest modelRequest) {
            events.add(context);
        }

        @Override
        public void afterModel(
                AgentLifecycleContext context, AgentRequest request,
                AgentRunState state, ModelResponse response) {
            events.add(context);
        }

        @Override
        public void afterDecision(
                AgentLifecycleContext context, AgentRequest request,
                AgentRunState state, AgentDecision decision) {
            events.add(context);
        }

        @Override
        public void beforeAction(
                AgentLifecycleContext context, AgentRequest request,
                AgentRunState state, AgentAction action) {
            events.add(context);
        }

        @Override
        public void afterAction(
                AgentLifecycleContext context, AgentRequest request, AgentRunState state,
                AgentAction action, AgentActionResult result) {
            events.add(context);
        }

        @Override
        public void afterTurn(
                AgentLifecycleContext context, AgentRequest request, AgentRunState state,
                AgentDecision decision, List<AgentActionResult> results) {
            events.add(context);
        }

        @Override
        public void onProtocolError(
                AgentLifecycleContext context, AgentRequest request,
                AgentRunState state, AgentProtocolException error) {
            events.add(context);
            protocolErrors.incrementAndGet();
        }

        @Override
        public void beforeCheckpoint(
                AgentLifecycleContext context, AgentRequest request, AgentRunSnapshot snapshot) {
            events.add(context);
        }

        @Override
        public void afterCheckpoint(
                AgentLifecycleContext context, AgentRequest request, AgentRunSnapshot snapshot) {
            events.add(context);
        }

        @Override
        public void onStop(
                AgentLifecycleContext context, AgentRequest request,
                AgentRunState state, AgentStopDecision decision) {
            events.add(context);
        }

        @Override
        public void onError(
                AgentLifecycleContext context, AgentRequest request,
                AgentRunState state, RuntimeException error) {
            events.add(context);
        }

        @Override
        public void afterRun(
                AgentLifecycleContext context, AgentRequest request, AgentResult result) {
            events.add(context);
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
