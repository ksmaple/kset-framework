package com.kset.agent.workflow;

import com.kset.agent.dto.AgentWorkflowRequest;
import com.kset.agent.dto.AgentWorkflowResult;

/**
 * 工作流引擎领域接口
 */
public interface WorkflowEngine {

    /**
     * 同步执行工作流
     *
     * @param request 工作流请求
     * @return 执行结果
     */
    AgentWorkflowResult execute(AgentWorkflowRequest request);

    /**
     * 恢复等待用户输入的工作流。
     */
    AgentWorkflowResult resume(String taskId, AgentWorkflowRequest request);

    /** 从最后一个已完成节点恢复因服务重启暂停的工作流。 */
    AgentWorkflowResult resumeInterrupted(String taskId, boolean retryUnknownTool);

    /**
     * 根据任务标识查询状态
     *
     * @param taskId 任务标识
     * @return 任务状态快照
     */
    WorkflowTaskStatus queryStatus(String taskId);

    /**
     * 取消任务
     *
     * @param taskId 任务标识
     * @return 是否取消成功
     */
    boolean cancel(String taskId);
}
