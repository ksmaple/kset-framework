package com.kset.monitor.interceptor;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 框架调用上下文（供 {@link FrameworkInterceptor} 使用）。
 */
public final class InvocationContext {

    private final String framework;
    private final String type;
    private final String name;
    private final long startNanos;
    private final Map<String, Object> attributes;

    public InvocationContext(String framework, String type, String name) {
        this(framework, type, name, System.nanoTime(), new HashMap<>());
    }

    private InvocationContext(String framework, String type, String name, long startNanos,
                              Map<String, Object> attributes) {
        this.framework = framework;
        this.type = type;
        this.name = name;
        this.startNanos = startNanos;
        this.attributes = attributes;
    }

    public String getFramework() {
        return framework;
    }

    public String getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public long getStartNanos() {
        return startNanos;
    }

    public long elapsedMillis() {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    public Map<String, Object> getAttributes() {
        return Collections.unmodifiableMap(attributes);
    }

    public void setAttribute(String key, Object value) {
        if (key != null && value != null) {
            attributes.put(key, value);
        }
    }
}
