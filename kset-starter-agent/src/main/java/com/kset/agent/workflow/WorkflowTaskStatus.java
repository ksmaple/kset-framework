package com.kset.agent.workflow;

import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 工作流任务状态快照（查询用）
 */
@Data
public class WorkflowTaskStatus {

    private String taskId;
    private TaskStatus status;
    private String executionStage;
    private String mode;
    private int currentStep;
    private int totalSteps;
    private String finalAnswer;
    private boolean finished;
    private List<Step> steps;
    private Date createdAt;
    private Date updatedAt;

    @Data
    public static class Step {

        private Integer step;
        private String status;
        private String nodeType;
        private String thought;
        private String action;
        private String actionInput;
        private String observation;
        private String errorMsg;
        private String toolName;
        private Long durationMs;
        private String executionId;
        private String planTaskId;
        private String parentExecutionId;
        private String executionType;
        private String stage;
        private String executionStage;
        private String displayTitle;
        private String displayContent;
        private Date startedAt;
        private Date completedAt;
    }
}
