package com.kset.common.context;

import java.util.Objects;

public final class KsetContextKey<T> {

    private final String name;
    private final Class<T> type;
    private final boolean propagatable;
    private final boolean sensitive;

    private KsetContextKey(String name, Class<T> type, boolean propagatable, boolean sensitive) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("context key name must not be blank");
        }
        this.name = name.trim();
        this.type = Objects.requireNonNull(type, "type");
        this.propagatable = propagatable;
        this.sensitive = sensitive;
    }

    public static <T> KsetContextKey<T> of(String name, Class<T> type) {
        return new KsetContextKey<>(name, type, true, false);
    }

    public static <T> KsetContextKey<T> of(String namespace, String name, Class<T> type) {
        return of(namespacedName(namespace, name), type);
    }

    public static <T> KsetContextKey<T> of(String name, Class<T> type, boolean propagatable, boolean sensitive) {
        return new KsetContextKey<>(name, type, propagatable, sensitive);
    }

    public static <T> KsetContextKey<T> of(String namespace, String name, Class<T> type,
                                          boolean propagatable, boolean sensitive) {
        return of(namespacedName(namespace, name), type, propagatable, sensitive);
    }

    public String getName() {
        return name;
    }

    public Class<T> getType() {
        return type;
    }

    public boolean isPropagatable() {
        return propagatable;
    }

    public boolean isSensitive() {
        return sensitive;
    }

    public boolean accepts(Object value) {
        return value == null || type.isInstance(value);
    }

    public T cast(Object value) {
        return value != null ? type.cast(value) : null;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof KsetContextKey<?> other)) {
            return false;
        }
        return Objects.equals(name, other.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return name;
    }

    private static String namespacedName(String namespace, String name) {
        if (namespace == null || namespace.isBlank()) {
            throw new IllegalArgumentException("context key namespace must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("context key name must not be blank");
        }
        return namespace.trim() + "." + name.trim();
    }
}
