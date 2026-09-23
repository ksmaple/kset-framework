package com.kset.agent.model;

import lombok.Getter;

import java.util.Arrays;

/**
 * AI 调用维度类型
 *
 * <p>用于区分不同场景的 AI Token 消耗统计，支持未来扩展新维度。
 */
@Getter
public enum AiCallDimension {

    CHAT("CHAT", "AI 聊天"),
    RAG("RAG", "RAG 问答"),
    CHAT_MEMORY("CHAT_MEMORY", "多轮记忆对话"),
    AGENT("AGENT", "Agent 工具调用"),
    DOC_EXTRACT("DOC_EXTRACT", "文档 AI 提取"),
    VISION("VISION", "多模态图片理解"),
    QUERY_UNDERSTANDING("QUERY_UNDERSTANDING", "查询理解"),
    NL2SQL("NL2SQL", "自然语言转 SQL"),
    EMBEDDING("EMBEDDING", "Embedding 向量化"),
    UNKNOWN("UNKNOWN", "未知维度");

    private final String code;
    private final String description;

    AiCallDimension(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public static AiCallDimension of(String code) {
        if (code == null || code.isBlank()) {
            return UNKNOWN;
        }
        return Arrays.stream(values())
                .filter(d -> d.code.equalsIgnoreCase(code))
                .findFirst()
                .orElse(UNKNOWN);
    }
}
