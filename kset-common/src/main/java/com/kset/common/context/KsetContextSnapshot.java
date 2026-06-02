package com.kset.common.context;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class KsetContextSnapshot {

    private final Map<String, Object> values;

    public KsetContextSnapshot(Map<String, Object> values) {
        this.values = values == null || values.isEmpty()
                ? Map.of()
                : Map.copyOf(new LinkedHashMap<>(values));
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public Map<String, Object> getValues() {
        return values;
    }

    public <T> Optional<T> get(KsetContextKey<T> key) {
        if (key == null) {
            return Optional.empty();
        }
        Object value = values.get(key.getName());
        return value != null ? Optional.of(key.cast(value)) : Optional.empty();
    }
}
