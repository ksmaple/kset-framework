package com.kset.agent.workflow;

/**
 * 工作流步骤状态
 */
public enum StepStatus {
    /**
     * 待执行
     */
    PENDING,
    /**
     * 执行中
     */
    RUNNING,
    /**
     * 已完成
     */
    COMPLETED,
    /**
     * 已失败
     */
    FAILED,
    /**
     * 已跳过
     */
    SKIPPED,
    /**
     * 已超时
     */
    TIMEOUT
}
