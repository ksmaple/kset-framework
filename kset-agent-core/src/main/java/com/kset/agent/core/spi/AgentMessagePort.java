package com.kset.agent.core.spi;

/**
 * Agent 文案与语言策略端口（i18n 由宿主应用接入）。
 */
public interface AgentMessagePort {

    /**
     * 按文案编码取消息；无对应文案时实现方应返回可读兜底。
     */
    String get(String code);

    /**
     * 最终回答节点的展示标题。
     */
    String finalAnswerTitle();

    /**
     * 面向模型的输出语言策略（如 zh / en / auto）。
     */
    String languageForModel();
}
