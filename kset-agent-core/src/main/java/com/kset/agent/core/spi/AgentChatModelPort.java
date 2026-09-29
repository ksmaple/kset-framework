package com.kset.agent.core.spi;

/**
 * Agent 编排调用的聊天模型端口。
 *
 * <p>宿主应用必须提供实现（通常桥接现有模型调用服务），否则编排执行器不会装配。
 */
public interface AgentChatModelPort {

    /**
     * 当前激活模型的描述（名称、模型标识、协议），用于日志与告警。
     */
    String activeModelDescription();

    /**
     * 带系统提示的结构化调用，按本轮输出预算覆盖输出上限。
     */
    String chatWithSystem(String systemPrompt, String userMessage, int maxOutputTokens);

    /**
     * 判断响应是否为降级/兜底响应（触发重试）。
     */
    boolean isFallbackResponse(String raw);
}
