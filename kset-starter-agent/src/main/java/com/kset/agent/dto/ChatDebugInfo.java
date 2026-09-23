package com.kset.agent.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ChatDebugInfo {

    private boolean enabled = true;

    private String rawAnswer;

    private IntentAnalysis intentAnalysis;

    private List<LlmRoundDebug> llmRounds = new ArrayList<>();

    private Object toolCatalog;

    private Object projectScope;

    private List<PromptDebug> renderedPrompts = new ArrayList<>();

    private Object flowDecision;

    private Object sessionContext;

    private Object guardrails;

    private Object observations;

    private List<TraceStage> timeline = new ArrayList<>();

    public boolean hasContent() {
        return rawAnswer != null
                || intentAnalysis != null
                || toolCatalog != null
                || projectScope != null
                || (renderedPrompts != null && !renderedPrompts.isEmpty())
                || flowDecision != null
                || sessionContext != null
                || guardrails != null
                || observations != null
                || (timeline != null && !timeline.isEmpty())
                || (llmRounds != null && !llmRounds.isEmpty());
    }

    @Data
    public static class LlmRoundDebug {

        private int round;

        private String stage;

        private String model;

        /** 本轮模型请求耗时（毫秒），仅调试视图返回。 */
        private Integer durationMs;

        private String finishReason;

        private Integer inputTokens;

        private Integer outputTokens;

        private String rawResponse;

        /** 仅服务端持久化使用，API 调试视图继续返回受控长度的 rawResponse。 */
        @com.fasterxml.jackson.annotation.JsonIgnore
        private String rawResponseFull;

        private String thought;

        private String finalAnswer;

        private String modelRequest;

        private String modelOutput;

        private String protocolError;

        /** 调用尚未产生模型正文时的失败原因。 */
        private String errorMessage;
    }

    @Data
    public static class PromptDebug {

        private String promptCode;

        private String promptName;

        private String versionNo;

        private String renderedContent;

        private Object variables;
    }

    @Data
    public static class TraceStage {

        private String name;

        private String code;

        private int startMs;

        private int durationMs;

        private String status;

        private String icon;

        private Object detail;
    }
}
