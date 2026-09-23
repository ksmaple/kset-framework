package com.kset.agent.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Agent 模型结构化输出。
 */
@Data
public class AgentModelOutput {

    private String type;
    private String answer;
    private String plan;
    private QueryAnalysis queryAnalysis;
    private List<PlanTask> tasks;
    private ToolCall toolCall;
    private List<ToolCall> toolCalls;
    private Confirmation confirmation;
    private List<String> resourceRefs;
    private String memoryPriority;
    private String memorySummary;

    @Data
    public static class ToolCall {
        private String taskId;
        private String toolName;
        private Map<String, Object> arguments;
        private List<String> dependsOn;
    }

    @Data
    public static class PlanTask {
        private String taskId;
        private String title;
        private List<String> dependsOn;
    }

    @Data
    public static class QueryAnalysis {
        private List<String> mainQueries;
        private List<String> relatedQueries;
        private List<String> englishQueries;
        private List<String> identifierQueries;
        private List<String> excludedTerms;
        private List<String> sourceHints;
    }

    @Data
    public static class Confirmation {
        private String confirmationId;
        private String kind;
        private String title;
        private String message;
        private String selectionType;
        private Boolean required;
        private String inputPlaceholder;
        private List<Option> options;
    }

    @Data
    public static class Option {
        private String id;
        private String label;
        private Map<String, Object> value;
    }
}
