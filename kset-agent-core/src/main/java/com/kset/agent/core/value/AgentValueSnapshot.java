package com.kset.agent.core.value;

import java.lang.reflect.Array;
import java.util.AbstractMap;
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

    /** Tool fields retain every supplied key, including fields with explicit null values. */
    public static Map<String, Object> toolFields(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        return snapshotMap(values, new IdentityHashMap<>(), false);
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
        if (values instanceof SnapshotMap frozen) {
            if (rejectNullTopLevelValues && frozen.hasNullValues) {
                throw new IllegalArgumentException("structured map values must not be null");
            }
            return frozen;
        }
        enter(values, visiting);
        try {
            Map<String, Object> result = new LinkedHashMap<>();
            boolean hasNullValues = false;
            for (Map.Entry<?, ?> entry : values.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw new IllegalArgumentException("structured map keys must be strings");
                }
                if (rejectNullTopLevelValues && entry.getValue() == null) {
                    throw new IllegalArgumentException("structured map values must not be null");
                }
                hasNullValues |= entry.getValue() == null;
                result.put(key, snapshot(entry.getValue(), visiting));
            }
            return new SnapshotMap(result, hasNullValues);
        } finally {
            visiting.remove(values);
        }
    }

    private static final class SnapshotMap extends AbstractMap<String, Object>
            implements java.io.Serializable {

        private static final long serialVersionUID = 1L;

        private final Map<String, Object> values;
        private final boolean hasNullValues;

        private SnapshotMap(Map<String, Object> values, boolean hasNullValues) {
            this.values = Collections.unmodifiableMap(values);
            this.hasNullValues = hasNullValues;
        }

        @Override
        public Set<Entry<String, Object>> entrySet() {
            return values.entrySet();
        }

        @Override
        public Object get(Object key) {
            return values.get(key);
        }

        @Override
        public boolean containsKey(Object key) {
            return values.containsKey(key);
        }

        @Override
        public int size() {
            return values.size();
        }
    }

    private static void enter(Object value, IdentityHashMap<Object, Boolean> visiting) {
        if (visiting.put(value, Boolean.TRUE) != null) {
            throw new IllegalArgumentException("cyclic structured values are not supported");
        }
    }
}
