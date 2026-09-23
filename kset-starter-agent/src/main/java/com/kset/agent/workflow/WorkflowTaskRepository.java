package com.kset.agent.workflow;

import com.kset.agent.workflow.WorkflowTask;
import com.kset.agent.workflow.WorkflowStep;

import java.util.List;
import java.util.Optional;

/**
 * 工作流任务仓库接口
 */
public interface WorkflowTaskRepository {

    /**
     * 保存任务
     */
    void save(WorkflowTask task);

    /**
     * 更新任务状态
     */
    void updateStatus(WorkflowTask task, String ownerId, long fencingToken);

    /**
     * 根据任务标识查询
     */
    Optional<WorkflowTask> findByTaskId(String taskId);

    /** 原子抢占一个暂停任务，防止多实例重复恢复。 */
    long claimExecution(String taskId, String ownerId, long leaseSeconds);

    boolean renewExecution(String taskId, String ownerId, long fencingToken, long leaseSeconds);

    /**
     * 服务重启恢复：将未完成执行任务批量标记为暂停，返回影响行数
     */
    int pauseExpiredExecutions();

    /**
     * 保存步骤
     */
    void saveStep(WorkflowStep step);

    /** 按 taskId + executionId 幂等写入节点，并按需在同一事务推进任务进度。 */
    void saveCheckpoint(WorkflowStep step, WorkflowTask task, boolean advanceTask,
                        String ownerId, long fencingToken);

    /**
     * 更新步骤
     */
    void updateStep(WorkflowStep step);

    /**
     * 查询任务下所有步骤
     */
    List<WorkflowStep> findStepsByTaskId(String taskId);
}
