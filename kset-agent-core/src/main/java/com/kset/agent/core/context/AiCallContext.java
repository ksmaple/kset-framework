package com.kset.agent.core.context;

import com.kset.agent.core.model.AiCallDimension;

/**
 * Thread-local AI call context.
 */
public class AiCallContext implements AutoCloseable {

    private static final ThreadLocal<AiCallContext> HOLDER = new ThreadLocal<>();

    private final AiCallDimension dimension;
    private final String sessionId;
    private final String memoryId;
    private final AiCallContext previous;

    private AiCallContext(AiCallDimension dimension, String sessionId, String memoryId, AiCallContext previous) {
        this.dimension = dimension;
        this.sessionId = sessionId;
        this.memoryId = memoryId;
        this.previous = previous;
    }

    public static AiCallContext withDimension(AiCallDimension dimension) {
        return withDimension(dimension, null, null);
    }

    public static AiCallContext withDimension(AiCallDimension dimension, String sessionId) {
        return withDimension(dimension, sessionId, sessionId);
    }

    public static AiCallContext withDimension(AiCallDimension dimension, String sessionId, String memoryId) {
        AiCallContext previous = HOLDER.get();
        AiCallContext ctx = new AiCallContext(dimension, sessionId, memoryId, previous);
        HOLDER.set(ctx);
        return ctx;
    }

    public static AiCallDimension getDimension() {
        AiCallContext ctx = HOLDER.get();
        return ctx != null ? ctx.dimension : AiCallDimension.UNKNOWN;
    }

    public static String getSessionId() {
        AiCallContext ctx = HOLDER.get();
        return ctx != null ? ctx.sessionId : null;
    }

    public static String getMemoryId() {
        AiCallContext ctx = HOLDER.get();
        return ctx != null ? ctx.memoryId : null;
    }

    @Override
    public void close() {
        if (previous != null) {
            HOLDER.set(previous);
        } else {
            HOLDER.remove();
        }
    }
}
