package com.kset.agent.core.action;

import com.kset.agent.core.value.AgentValueSnapshot;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Validated read-only tool graph carried by the optional tool_flow extension. */
public final class ToolFlowPlan {

    public static final String ACTION_TYPE = "tool_flow";
    public static final String RESULT_REFERENCE = "$toolResult";

    private final String summary;
    private final List<Call> calls;
    private final List<List<Call>> layers;

    private ToolFlowPlan(String summary, List<Call> calls, List<List<Call>> layers) {
        this.summary = summary;
        this.calls = List.copyOf(calls);
        this.layers = List.copyOf(layers);
    }

    public static ToolFlowPlan parse(Map<String, Object> payload) {
        if (payload == null || !payload.keySet().equals(Set.of("plan", "toolCalls"))) {
            throw new IllegalArgumentException("tool_flow requires plan and toolCalls");
        }
        String summary = text(payload.get("plan"), "plan");
        if (!(payload.get("toolCalls") instanceof List<?> values) || values.size() < 2) {
            throw new IllegalArgumentException("tool_flow requires at least two toolCalls");
        }
        List<Call> calls = new ArrayList<>(values.size());
        Map<String, Call> byId = new LinkedHashMap<>();
        for (Object value : values) {
            Call call = parseCall(value);
            if (byId.putIfAbsent(call.callId(), call) != null) {
                throw new IllegalArgumentException("tool_flow callIds must be unique");
            }
            calls.add(call);
        }
        for (Call call : calls) {
            for (String dependency : call.dependsOn()) {
                if (dependency.equals(call.callId()) || !byId.containsKey(dependency)) {
                    throw new IllegalArgumentException("tool_flow dependency is invalid");
                }
            }
            validateReferences(call.arguments(), call.dependsOn());
        }
        return new ToolFlowPlan(summary, calls, layers(calls));
    }

    public String summary() {
        return summary;
    }

    public List<Call> calls() {
        return calls;
    }

    public List<List<Call>> layers() {
        return layers;
    }

    private static Call parseCall(Object value) {
        if (!(value instanceof Map<?, ?> raw)
                || !Set.of("callId", "taskId", "toolName", "arguments", "dependsOn")
                .containsAll(raw.keySet())
                || !raw.keySet().containsAll(Set.of("callId", "toolName", "arguments"))) {
            throw new IllegalArgumentException("tool_flow call fields are invalid");
        }
        String callId = text(raw.get("callId"), "callId");
        String taskId = raw.containsKey("taskId") ? text(raw.get("taskId"), "taskId") : null;
        String toolName = text(raw.get("toolName"), "toolName");
        if (!(raw.get("arguments") instanceof Map<?, ?> rawArguments)) {
            throw new IllegalArgumentException("tool_flow arguments must be an object");
        }
        Map<String, Object> arguments = stringMap(rawArguments, "arguments");
        List<String> dependencies = new ArrayList<>();
        Object rawDependencies = raw.containsKey("dependsOn")
                ? raw.get("dependsOn") : List.of();
        if (!(rawDependencies instanceof List<?> items)) {
            throw new IllegalArgumentException("tool_flow dependsOn must be an array");
        }
        for (Object item : items) {
            String dependency = text(item, "dependsOn item");
            if (dependencies.contains(dependency)) {
                throw new IllegalArgumentException("tool_flow dependencies must be unique");
            }
            dependencies.add(dependency);
        }
        return new Call(callId, taskId, toolName,
                AgentValueSnapshot.toolFields(arguments), List.copyOf(dependencies));
    }

    private static void validateReferences(Object value, List<String> dependencies) {
        if (value instanceof Map<?, ?> map) {
            if (map.containsKey(RESULT_REFERENCE)) {
                reference(map, dependencies);
                return;
            }
            map.values().forEach(item -> validateReferences(item, dependencies));
        } else if (value instanceof List<?> list) {
            list.forEach(item -> validateReferences(item, dependencies));
        }
    }

    public static Reference reference(Map<?, ?> placeholder, List<String> dependencies) {
        if (placeholder.size() != 1
                || !(placeholder.get(RESULT_REFERENCE) instanceof Map<?, ?> raw)
                || !raw.keySet().equals(Set.of("callId", "path"))) {
            throw new IllegalArgumentException("tool result reference is invalid");
        }
        String callId = text(raw.get("callId"), "reference callId");
        if (!dependencies.contains(callId)) {
            throw new IllegalArgumentException("tool result reference requires direct dependency");
        }
        if (!(raw.get("path") instanceof String path)
                || (!path.isEmpty() && !path.startsWith("/"))) {
            throw new IllegalArgumentException("tool result reference path must be a JSON Pointer");
        }
        try {
            com.fasterxml.jackson.core.JsonPointer.compile(path);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("tool result reference path is invalid", error);
        }
        return new Reference(callId, path);
    }

    private static List<List<Call>> layers(List<Call> calls) {
        List<List<Call>> result = new ArrayList<>();
        Set<String> completed = new HashSet<>();
        while (completed.size() < calls.size()) {
            List<Call> ready = calls.stream()
                    .filter(call -> !completed.contains(call.callId())
                            && completed.containsAll(call.dependsOn()))
                    .toList();
            if (ready.isEmpty()) {
                throw new IllegalArgumentException("tool_flow dependencies must not contain a cycle");
            }
            result.add(ready);
            ready.forEach(call -> completed.add(call.callId()));
        }
        return List.copyOf(result);
    }

    private static Map<String, Object> stringMap(Map<?, ?> raw, String name) {
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, value) -> {
            if (!(key instanceof String field)) {
                throw new IllegalArgumentException(name + " keys must be strings");
            }
            result.put(field, value);
        });
        return result;
    }

    private static String text(Object value, String name) {
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException(name + " must be a non-blank string");
        }
        return text.trim();
    }

    public record Call(String callId, String taskId, String toolName,
                       Map<String, Object> arguments, List<String> dependsOn) { }

    public record Reference(String callId, String path) { }
}
