package com.kset.agent.core.spi;

/**
 * Agent 编排的模型接入端口（宿主必须提供的唯一模型 SPI）。
 *
 * <p>合并了原 {@code AgentChatModelPort}（调用）与 {@code AiModelProvider}（能力/预算描述）
 * 两套重叠接口：宿主只需实现一个 Bean 即可同时满足模型调用与输出预算计算。
 *
 * <p>调用方法 {@link #chatWithSystem} 与 {@link #isFallbackResponse} 必须实现；
 * 能力描述方法均有宽松默认值，宿主可按需覆盖以获得更精确的输出预算。
 */
public interface AgentModelPort {

    /**
     * 带系统提示的结构化调用，按本轮输出预算覆盖输出上限。
     */
    String chatWithSystem(String systemPrompt, String userMessage, int maxOutputTokens);

    /**
     * 判断响应是否为降级/兜底响应（触发重试）。
     */
    boolean isFallbackResponse(String raw);

    /**
     * 当前是否有已激活且可用的模型配置。
     */
    default boolean isAvailable() {
        return true;
    }

    /**
     * 当前激活模型的描述（名称、模型标识、协议），用于日志与告警；未激活时返回 "none"。
     */
    default String activeModelDescription() {
        return "unknown";
    }

    /**
     * 当前激活模型单次调用允许生成的最大 Token 数；{@code <=0} 表示未配置。
     */
    default int activeMaxOutputTokens() {
        return 0;
    }

    /**
     * 当前激活模型的上下文窗口 Token 数；{@code <=0} 表示未知。
     */
    default int activeContextWindowTokens() {
        return 0;
    }

    /**
     * 按本轮估算输入计算有效输出上限；默认直接取配置值。
     */
    default int effectiveMaxOutputTokens(int estimatedInputTokens) {
        return Math.max(0, activeMaxOutputTokens());
    }
}
