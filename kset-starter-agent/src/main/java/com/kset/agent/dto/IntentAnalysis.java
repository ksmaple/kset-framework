package com.kset.agent.dto;

import java.util.List;

/**
 * 意图分析结果
 *
 * @param needSearch          是否需要检索知识库
 * @param needDatabaseQuery  是否需要查询数据库（NL2SQL）
 * @param optimizedQuery    优化后的查询（更适合向量语义检索）
 * @param reasoning         分析原因（调试用）
 */
public record IntentAnalysis(
        String intent,

        String intentName,

        double confidence,

        String source,

        List<Candidate> candidates,

        boolean needDisambiguation,

        boolean needSearch,

        boolean needDatabaseQuery,

        String optimizedQuery,

        String reasoning
) {
    public IntentAnalysis {
        intent = blankToDefault(intent, inferIntent(needSearch, needDatabaseQuery, reasoning));
        intentName = blankToDefault(intentName, inferIntentName(intent));
        source = blankToDefault(source, inferSource(reasoning));
        confidence = clampConfidence(confidence <= 0 ? inferConfidence(source, reasoning) : confidence);
        candidates = candidates == null || candidates.isEmpty()
                ? List.of(new Candidate(intent, intentName, confidence, source, true))
                : List.copyOf(candidates);
        needDisambiguation = needDisambiguation || confidence < 0.60;
    }

    public IntentAnalysis(boolean needSearch, boolean needDatabaseQuery, String optimizedQuery, String reasoning) {
        this(null, null, 0, null, null, false, needSearch, needDatabaseQuery, optimizedQuery, reasoning);
    }

    public record Candidate(
            String id,

            String name,

            double score,

            String source,

            boolean selected
    ) {
    }

    private static String inferIntent(boolean needSearch, boolean needDatabaseQuery, String reasoning) {
        if (needDatabaseQuery) {
            return "database_query";
        }
        if (reasoning != null && reasoning.contains("项目能力外")) {
            return "out_of_scope";
        }
        if (needSearch) {
            return "document_question";
        }
        return "small_talk";
    }

    private static String inferIntentName(String intent) {
        if ("database_query".equals(intent)) {
            return "数据库查询";
        }
        if ("out_of_scope".equals(intent)) {
            return "能力外请求";
        }
        if ("document_question".equals(intent)) {
            return "文档问答";
        }
        return "闲聊反馈";
    }

    private static String inferSource(String reasoning) {
        if (reasoning == null) {
            return "LLM";
        }
        if (reasoning.startsWith("本地规则")) {
            return "LOCAL_RULE";
        }
        if (reasoning.contains("兜底")) {
            return "FALLBACK";
        }
        return "LLM";
    }

    private static double inferConfidence(String source, String reasoning) {
        if ("LOCAL_RULE".equals(source)) {
            return 0.95;
        }
        if ("FALLBACK".equals(source)) {
            return 0.50;
        }
        if (reasoning != null && reasoning.contains("项目能力外")) {
            return 0.85;
        }
        return 0.80;
    }

    private static double clampConfidence(double value) {
        if (value < 0) {
            return 0;
        }
        if (value > 1) {
            return 1;
        }
        return value;
    }

    private static String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
