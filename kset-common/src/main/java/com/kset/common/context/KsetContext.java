package com.kset.common.context;

import com.alibaba.ttl.TransmittableThreadLocal;
import com.kset.common.trace.TraceHeaders;
import org.slf4j.MDC;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class KsetContext {

    private static final TransmittableThreadLocal<Map<String, Object>> CURRENT =
            TransmittableThreadLocal.withInitialAndCopier(LinkedHashMap::new, KsetContext::copyPropagatable);
    private static final Map<String, KsetContextKey<?>> REGISTERED_KEYS = new ConcurrentHashMap<>();

    static {
        register(KsetContextKeys.LOGIN_USER);
        register(KsetContextKeys.TRACE_ID);
        register(KsetContextKeys.SPAN_ID);
        register(KsetContextKeys.GRAY_TAG);
        register(KsetContextKeys.TENANT_ID);
        register(KsetContextKeys.LANGUAGE);
    }

    private KsetContext() {
    }

    public static <T> void register(KsetContextKey<T> key) {
        if (key == null) {
            return;
        }
        REGISTERED_KEYS.merge(key.getName(), key, KsetContext::ensureSameType);
    }

    public static <T> Optional<T> get(KsetContextKey<T> key) {
        if (key == null) {
            return Optional.empty();
        }
        register(key);
        Object value = CURRENT.get().get(key.getName());
        return value != null ? Optional.of(key.cast(value)) : Optional.empty();
    }

    public static Map<String, Object> currentValues() {
        return Map.copyOf(new LinkedHashMap<>(CURRENT.get()));
    }

    public static <T> void put(KsetContextKey<T> key, T value) {
        if (key == null) {
            return;
        }
        register(key);
        if (!key.accepts(value)) {
            throw new IllegalArgumentException("context key " + key.getName() + " does not accept value type "
                    + value.getClass().getName());
        }
        if (value == null) {
            remove(key);
            return;
        }
        CURRENT.get().put(key.getName(), value);
        syncMdcPut(key.getName(), value);
    }

    public static <T> void remove(KsetContextKey<T> key) {
        if (key != null) {
            CURRENT.get().remove(key.getName());
            syncMdcRemove(key.getName());
        }
    }

    public static void clear() {
        CURRENT.remove();
        MDC.remove(TraceHeaders.TRACE_ID_KEY);
        MDC.remove(TraceHeaders.SPAN_ID_KEY);
        MDC.remove(TraceHeaders.GRAY_TAG_KEY);
    }

    public static KsetContextSnapshot capture() {
        return new KsetContextSnapshot(copyPropagatable(CURRENT.get()));
    }

    public static void restore(KsetContextSnapshot snapshot) {
        if (snapshot == null || snapshot.isEmpty()) {
            clear();
            return;
        }
        CURRENT.set(new LinkedHashMap<>(snapshot.getValues()));
        syncMdcFromCurrent();
    }

    public static KsetContextScope openScope(KsetContextSnapshot snapshot) {
        KsetContextSnapshot previous = capture();
        restore(snapshot);
        return new KsetContextScope(previous);
    }

    private static KsetContextKey<?> ensureSameType(KsetContextKey<?> current, KsetContextKey<?> incoming) {
        if (!current.getType().equals(incoming.getType())) {
            throw new IllegalStateException("context key type conflict for " + current.getName());
        }
        if (current.isPropagatable() != incoming.isPropagatable() || current.isSensitive() != incoming.isSensitive()) {
            throw new IllegalStateException("context key metadata conflict for " + current.getName());
        }
        return current;
    }

    private static Map<String, Object> copyPropagatable(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach((name, value) -> {
            KsetContextKey<?> key = REGISTERED_KEYS.get(name);
            if (key == null || key.isPropagatable()) {
                copy.put(name, value);
            }
        });
        return copy;
    }

    private static void syncMdcFromCurrent() {
        MDC.remove(TraceHeaders.TRACE_ID_KEY);
        MDC.remove(TraceHeaders.SPAN_ID_KEY);
        MDC.remove(TraceHeaders.GRAY_TAG_KEY);
        Map<String, Object> current = CURRENT.get();
        syncMdcPut(TraceHeaders.TRACE_ID_KEY, current.get(TraceHeaders.TRACE_ID_KEY));
        syncMdcPut(TraceHeaders.SPAN_ID_KEY, current.get(TraceHeaders.SPAN_ID_KEY));
        syncMdcPut(TraceHeaders.GRAY_TAG_KEY, current.get(TraceHeaders.GRAY_TAG_KEY));
    }

    private static void syncMdcPut(String keyName, Object value) {
        if (!(value instanceof String text)) {
            return;
        }
        if (TraceHeaders.TRACE_ID_KEY.equals(keyName)) {
            MDC.put(TraceHeaders.TRACE_ID_KEY, text);
        } else if (TraceHeaders.SPAN_ID_KEY.equals(keyName)) {
            MDC.put(TraceHeaders.SPAN_ID_KEY, text);
        } else if (TraceHeaders.GRAY_TAG_KEY.equals(keyName)) {
            MDC.put(TraceHeaders.GRAY_TAG_KEY, text);
        }
    }

    private static void syncMdcRemove(String keyName) {
        if (TraceHeaders.TRACE_ID_KEY.equals(keyName)) {
            MDC.remove(TraceHeaders.TRACE_ID_KEY);
        } else if (TraceHeaders.SPAN_ID_KEY.equals(keyName)) {
            MDC.remove(TraceHeaders.SPAN_ID_KEY);
        } else if (TraceHeaders.GRAY_TAG_KEY.equals(keyName)) {
            MDC.remove(TraceHeaders.GRAY_TAG_KEY);
        }
    }
}
