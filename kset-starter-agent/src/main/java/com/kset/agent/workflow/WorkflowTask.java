package com.kset.agent.workflow;

import com.kset.agent.workflow.TaskStatus;
import com.kset.agent.workflow.AgentExecutionStage;

import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 工作流任务聚合根
 */
@Data
public class WorkflowTask {

    private Long id;
    private String taskId;
    private String mode;
    private TaskStatus status;
    private AgentExecutionStage executionStage;
    private Long userId;
    private String sessionId;
    private String messageId;
    private Long projectId;
    private String originalTask;
    private String businessPrompt;
    private String finalAnswer;
    private boolean finished;
    private int maxSteps;
    private int currentStep;
    private boolean enableTools;
    private String metadata;
    private String requestSnapshot;
    private String executionSnapshot;
    private String ownerId;
    private Date leaseExpireAt;
    private long fencingToken;
    private long version;
    private Date createdAt;
    private Date updatedAt;
    private Date completedAt;
    private Integer del;

    private List<WorkflowStep> steps;
}
