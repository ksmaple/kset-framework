package com.kset.agent.workflow;

/**
 * 工作流任务状态
 */
public enum TaskStatus {
    /**
     * 待执行
     */
    PENDING,
    /**
     * 执行中
     */
    RUNNING,
    /**
     * 等待用户输入
     */
    WAITING_INPUT,
    /**
     * 已暂停
     */
    PAUSED,
    /**
     * 已完成
     */
    COMPLETED,
    /**
     * 已失败
     */
    FAILED,
    /**
     * 重试中
     */
    RETRYING,
    /**
     * 已取消
     */
    CANCELLED,
    /**
     * 已超时
     */
    TIMEOUT
}
