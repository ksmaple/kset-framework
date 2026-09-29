package com.kset.agent.core.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Data
public class AgentWorkflowResult {

    private String answer;

    /** 稳定错误码；成功结果为空。 */
    private String errorCode;

    /** 错误码对应的默认提示；外层可按 errorCode 替换。 */
    private String errorMessage;

    private List<Step> steps;

    private int totalSteps;

    private boolean finished;

    private boolean requiresConfirmation;

    private AgentConfirmationDTO confirmation;

    private List<AiOutputResourceDTO> resources;

    private String taskId;

    private String mode;

    private String taskStatus;

    private String executionStage;

    private String audienceProfile;

    private String audienceSource;

    private String promptCode;

    @JsonIgnore
    private String memoryPriority;

    @JsonIgnore
    private String memorySummary;

    private ChatDebugInfo debug;

    /**
     * SSE 完成事件只返回工作流摘要；完整步骤继续由服务端检查点和恢复接口持有。
     */
    public AgentWorkflowResult toStreamSummary() {
        AgentWorkflowResult summary = new AgentWorkflowResult();
        summary.setAnswer(answer);
        summary.setErrorCode(errorCode);
        summary.setErrorMessage(errorMessage);
        summary.setTotalSteps(totalSteps);
        summary.setFinished(finished);
        summary.setRequiresConfirmation(requiresConfirmation);
        summary.setConfirmation(confirmation);
        summary.setTaskId(taskId);
        summary.setMode(mode);
        summary.setTaskStatus(taskStatus);
        summary.setExecutionStage(executionStage);
        summary.setAudienceProfile(audienceProfile);
        summary.setAudienceSource(audienceSource);
        summary.setPromptCode(promptCode);
        return summary;
    }

    @Data
    public static class Step {
        private int step;

        @JsonIgnore
        private String thought;

        private String action;

        @JsonIgnore
        private String actionInput;

        private String type;

        @JsonIgnore
        @Getter(AccessLevel.NONE)
        @Setter(AccessLevel.NONE)
        private boolean finalResult;

        private String toolName;

        @JsonIgnore
        private String toolArguments;

        @JsonIgnore
        private String observation;

        private Boolean toolSuccess;

        @JsonIgnore
        private String protocolError;

        @JsonIgnore
        private String modelRequest;

        @JsonIgnore
        private String modelOutput;

        @JsonIgnore
        private String memoryPriority;

        @JsonIgnore
        private String memorySummary;

        private boolean finalAnswer;

        private String stage;

        private String executionStage;

        private String displayTitle;

        private String displayContent;

        /** 用户可见的脱敏查询摘要，不包含原始模型请求或完整工具参数。 */
        private String query;

        private String executionId;

        private String planTaskId;

        private String parentExecutionId;

        private String executionType;

        private String executionStatus;

        private Long durationMs;

        private QueryAnalysis queryAnalysis;

        private List<PlanTask> planTasks;

        /** 当前事件是否只包含相对上一事件的变化。 */
        private boolean incremental;

        /** 当前事件新增的脱敏输入、输出与计数摘要。 */
        private StepDelta delta;

        private boolean requiresConfirmation;

        private AgentConfirmationDTO confirmation;

        private List<AiOutputResourceDTO> resources;

        @JsonProperty("isFinal")
        public boolean isFinal() {
            return finalResult;
        }

        @JsonProperty("isFinal")
        public void setFinal(boolean finalResult) {
            this.finalResult = finalResult;
        }
    }

    @Data
    public static class StepDelta {
        private String inputSummary;
        private String outputSummary;
        private Integer inputChars;
        private Integer outputChars;
        private Integer itemCount;
        private Integer evidenceCount;
        private Integer inputTokens;
        private Integer outputTokens;
        private Boolean truncated;
        private Boolean noChange;
        private Boolean newEvidence;
        private String evidenceLevel;
        private List<String> nextCapabilities;
    }

    @Data
    public static class PlanTask {
        private String taskId;
        private String title;
        private List<String> dependsOn;
        private String status;
        private String displayContent;
        private Long durationMs;
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
}
