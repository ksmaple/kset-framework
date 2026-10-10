package com.kset.agent.core.value;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Recursively snapshots JSON-like containers while preserving opaque immutable values. */
public final class AgentValueSnapshot {

    private AgentValueSnapshot() {
    }

    public static Map<String, Object> map(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        return snapshotMap(values, new IdentityHashMap<>(), true);
    }

    public static Object value(Object value) {
        return snapshot(value, new IdentityHashMap<>());
    }

    private static Object snapshot(Object value, IdentityHashMap<Object, Boolean> visiting) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> map) {
            return snapshotMap(map, visiting, false);
        }
        if (value instanceof Set<?> set) {
            enter(set, visiting);
            try {
                Set<Object> result = new LinkedHashSet<>();
                set.forEach(item -> result.add(snapshot(item, visiting)));
                return Collections.unmodifiableSet(result);
            } finally {
                visiting.remove(set);
            }
        }
        if (value instanceof Collection<?> collection) {
            enter(collection, visiting);
            try {
                List<Object> result = new ArrayList<>(collection.size());
                collection.forEach(item -> result.add(snapshot(item, visiting)));
                return Collections.unmodifiableList(result);
            } finally {
                visiting.remove(collection);
            }
        }
        if (value.getClass().isArray()) {
            enter(value, visiting);
            try {
                int length = Array.getLength(value);
                List<Object> result = new ArrayList<>(length);
                for (int index = 0; index < length; index++) {
                    result.add(snapshot(Array.get(value, index), visiting));
                }
                return Collections.unmodifiableList(result);
            } finally {
                visiting.remove(value);
            }
        }
        return value;
    }

    private static Map<String, Object> snapshotMap(
            Map<?, ?> values, IdentityHashMap<Object, Boolean> visiting,
            boolean rejectNullTopLevelValues) {
        enter(values, visiting);
        try {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : values.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw new IllegalArgumentException("structured map keys must be strings");
                }
                if (rejectNullTopLevelValues && entry.getValue() == null) {
                    throw new IllegalArgumentException("structured map values must not be null");
                }
                result.put(key, snapshot(entry.getValue(), visiting));
            }
            return Collections.unmodifiableMap(result);
        } finally {
            visiting.remove(values);
        }
    }

    private static void enter(Object value, IdentityHashMap<Object, Boolean> visiting) {
        if (visiting.put(value, Boolean.TRUE) != null) {
            throw new IllegalArgumentException("cyclic structured values are not supported");
        }
    }
}
