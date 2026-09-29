package com.kset.agent.core.memory;

/**
 * 记忆类型：用于区分长期记忆中存储的内容类别
 */
public enum MemoryType {
    /**
     * 用户查询
     */
    USER_QUERY,
    /**
     * AI 回复
     */
    AI_RESPONSE,
    /**
     * 工具执行结果
     */
    TOOL_RESULT,
    /**
     * 会话摘要
     */
    SUMMARY,
    /**
     * 提取的事实
     */
    FACT,
    /**
     * 用户偏好
     */
    PREFERENCE
}
