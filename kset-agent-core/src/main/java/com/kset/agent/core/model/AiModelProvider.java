package com.kset.agent.core.model;

import com.kset.agent.core.model.ImageContent;
import com.kset.agent.core.model.ToolSpec;

import java.util.List;

/**
 * AI 模型提供者接口（领域层抽象）
 *
 * <p>封装不同平台的模型调用，支持 OpenAI/Kimi、Anthropic、Azure、Google、Ollama 等。
 * 新增平台时只需实现此接口并注册为 Spring Bean。
 *
 * <p>接口设计遵循"框架无关"原则：方法签名中不出现任何具体 AI 框架的类型，
 * 确保切换底层框架（如 LangChain4j → Spring AI）时领域层和应用层无需修改。
 */
public interface AiModelProvider {

    /**
     * 当前是否有已激活且可用的模型配置
     */
    default boolean isAvailable() {
        return true;
    }

    /**
     * 当前激活模型的描述（名称、模型标识、协议），用于日志与告警；未激活时返回 "none"
     */
    default String activeModelDescription() {
        return "unknown";
    }

    /**
     * 当前激活模型单次调用允许生成的最大 Token 数。
     */
    default int activeMaxOutputTokens() {
        return 0;
    }

    default int activeContextWindowTokens() {
        return 0;
    }

    default int effectiveMaxOutputTokens(int estimatedInputTokens) {
        int configured = activeMaxOutputTokens();
        return Math.max(0, configured);
    }

    /**
     * 回滚保留：agent-orchestration-protocol / AOP-20260903-16，恢复请求前按估算窗口削减输出。
     */
    default int effectiveMaxOutputTokensForRollback(int estimatedInputTokens) {
        int configured = activeMaxOutputTokens();
        int context = activeContextWindowTokens();
        if (configured <= 0) return 0;
        if (context <= 0 || estimatedInputTokens <= 0) return configured;
        return Math.max(1, Math.min(configured, context - estimatedInputTokens - 256));
    }

    /**
     * 简单对话
     */
    String chat(String userMessage);

    /**
     * 带系统提示的对话
     */
    String chatWithSystem(String systemPrompt, String userMessage);

    /** Agent 结构化调用按本轮输入预算覆盖输出上限。 */
    default String chatWithSystem(String systemPrompt, String userMessage, int maxOutputTokens) {
        return chatWithSystem(systemPrompt, userMessage);
    }

    /**
     * 带系统提示的流式对话：按增量返回正文文本（已过滤推理内容）。
     *
     * <p>仅用于面向用户的最终回答场景；需要完整结构化输出的链路不得使用。
     */
    default reactor.core.publisher.Flux<String> streamChatWithSystem(String systemPrompt, String userMessage) {
        throw new UnsupportedOperationException("当前模型提供者不支持流式输出");
    }

    /**
     * 带系统提示的对话，返回结构化对象
     *
     * @param systemPrompt 系统提示
     * @param userMessage  用户消息
     * @param outputType   期望返回的对象类型
     * @param <T>          返回类型
     * @return 解析后的结构化对象
     */
    default <T> T chatWithSystemAndReturn(String systemPrompt, String userMessage, Class<T> outputType) {
        throw new UnsupportedOperationException("当前模型提供者不支持结构化输出");
    }

    /**
     * 带系统提示与图片的多模态对话
     *
     * @param systemPrompt 系统提示
     * @param userText     用户文本消息
     * @param images       图片列表（{@link ImageContent}）
     * @return 模型回复
     */
    default String chatWithImages(String systemPrompt, String userText, List<ImageContent> images) {
        throw new UnsupportedOperationException("当前模型提供者不支持多模态图片输入");
    }

    /**
     * 带系统提示和多轮对话记忆的对话
     *
     * @param systemPrompt 系统提示
     * @param userMessage  用户消息
     * @param memoryId     对话记忆标识（如 sessionId、userId）
     * @return 模型回复
     */
    default String chatWithMemory(String systemPrompt, String userMessage, Object memoryId) {
        throw new UnsupportedOperationException("当前模型提供者不支持对话记忆");
    }

    /**
     * 带系统提示和工具调用的对话
     *
     * @param systemPrompt 系统提示
     * @param userMessage  用户消息
     * @param tools        可用工具规格列表（领域层抽象 {@link ToolSpec}）
     * @return 模型回复
     */
    default String chatWithTools(String systemPrompt, String userMessage, List<ToolSpec> tools) {
        throw new UnsupportedOperationException("当前模型提供者不支持工具调用");
    }

    /**
     * 带系统提示和工具实例的对话（支持 {@code @Tool} 注解自动执行）
     *
     * @param systemPrompt  系统提示
     * @param userMessage   用户消息
     * @param toolsInstance 包含 {@code @Tool} 注解方法的工具实例
     * @return 模型回复
     */
    default String chatWithTools(String systemPrompt, String userMessage, Object toolsInstance) {
        throw new UnsupportedOperationException("当前模型提供者不支持工具调用");
    }

    /**
     * 清除指定对话的记忆
     *
     * @param memoryId 对话记忆标识
     */
    default void clearMemory(Object memoryId) {
        throw new UnsupportedOperationException("当前模型提供者不支持对话记忆管理");
    }

    /**
     * 设置对话记忆窗口大小
     *
     * @param maxMessages 最大保留消息数
     */
    default void setMaxMemoryMessages(int maxMessages) {
        throw new UnsupportedOperationException("当前模型提供者不支持对话记忆管理");
    }
}
