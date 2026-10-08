package com.kset.agent.core.model;

import java.util.LinkedHashMap;
import java.util.Map;

/** Native tool call emitted by a provider that supports function calling. */
public record ModelToolCall(String id, String name, Map<String, Object> arguments) {

    public ModelToolCall {
        arguments = arguments == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(arguments));
    }
}
