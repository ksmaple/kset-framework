package com.kset.agent.core.dto;

import com.kset.agent.core.memory.AgentMemoryContext;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Data
public class AgentWorkflowRequest {

    public String getMode() {
        return "react";
    }

    @NotBlank(message = "任务描述不能为空")
    private String task;

    /** 最大编排轮次；0 表示未指定，回落到 ai.agent.orchestration.max-steps。 */
    private int maxSteps = 0;

    private boolean enableTools = true;

    private List<String> allowedTools;

    private String businessPrompt;

    private String inputContext;

    private AgentMemoryContext memoryContext;

    private String confirmedToolName;

    private String confirmedPlanTaskId;

    private Map<String, Object> confirmedToolArguments;

    private int initialStepOffset;

    private boolean taskPlanCreated;

    private List<String> plannedTaskIds;

    private String storageName;

    private Long projectId;

    /** 工作流读取范围；写入节点仍必须使用 projectId 指定目标项目。 */
    private List<Long> projectIds;

    private String sessionId;

    private String messageId;

    private String audienceProfile;

    private String audienceSource;

    private String audienceInstruction;

    private String promptCode;

    private String promptInstruction;

    private String platform;

    /** 原始用户问题，仅用于完成阶段写入对话记忆。 */
    private String memoryQuestion;

    /** 从持久化状态恢复的执行快照，不并入请求快照。 */
    @com.fasterxml.jackson.annotation.JsonIgnore
    private transient AgentExecutionSnapshot executionSnapshot;

    /** 任务级编排阶段回调，由工作流引擎绑定。 */
    @com.fasterxml.jackson.annotation.JsonIgnore
    private transient Consumer<com.kset.agent.core.workflow.AgentExecutionStage> executionStageCallback;

    /** 有界重试状态回调，由工作流引擎绑定。 */
    @com.fasterxml.jackson.annotation.JsonIgnore
    private transient Consumer<com.kset.agent.core.workflow.TaskStatus> taskStatusCallback;

    /** 步骤回调：仅在同步聊天链路内存中传递，用于 SSE 实时推送执行进度，不参与序列化 */
    @com.fasterxml.jackson.annotation.JsonIgnore
    private transient java.util.function.Consumer<AgentWorkflowResult.Step> stepCallback;

    /** 节点检查点回调：失败时必须中止流程，避免节点成功但状态未落库。 */
    @com.fasterxml.jackson.annotation.JsonIgnore
    private transient java.util.function.Consumer<AgentWorkflowResult.Step> checkpointCallback;

    /** 用户取消或执行租约失效检查，仅在当前执行进程内使用。 */
    @com.fasterxml.jackson.annotation.JsonIgnore
    private transient java.util.function.BooleanSupplier stopRequested;
}
