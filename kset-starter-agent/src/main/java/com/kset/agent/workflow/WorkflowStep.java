package com.kset.agent.workflow;

import com.kset.agent.workflow.StepStatus;
import lombok.Data;

import java.util.Date;

/**
 * 工作流步骤实体
 */
@Data
public class WorkflowStep {

    private Long id;
    private String taskId;
    private Integer stepIndex;
    private StepStatus status;
    private String nodeType;
    private String inputText;
    private String outputText;
    private String thought;
    private String action;
    private String actionInput;
    private String observation;
    private String errorMsg;
    private Integer retryCount;
    private String toolName;
    private String toolResult;
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
    private Long durationMs;
    private Date createdAt;
    private Integer del;
}
