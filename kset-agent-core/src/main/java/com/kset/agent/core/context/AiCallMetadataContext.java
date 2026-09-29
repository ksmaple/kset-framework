package com.kset.agent.core.context;

/**
 * 保存当前线程最近一次模型调用的响应元数据。
 */
public final class AiCallMetadataContext {

    private static final ThreadLocal<Metadata> CURRENT = new ThreadLocal<>();

    private AiCallMetadataContext() {
    }

    public static void set(String finishReason, int inputTokens, int outputTokens) {
        CURRENT.set(new Metadata(finishReason, inputTokens, outputTokens));
    }

    public static Metadata get() {
        Metadata metadata = CURRENT.get();
        return metadata != null ? metadata : new Metadata("UNKNOWN", 0, 0);
    }

    public static Metadata take() {
        Metadata metadata = get();
        clear();
        return metadata;
    }

    public static void clear() {
        CURRENT.remove();
    }

    public record Metadata(String finishReason, int inputTokens, int outputTokens) {
    }
}
