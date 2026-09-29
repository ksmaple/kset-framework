package com.kset.agent.core;

/**
 * Agent 核心模块通用业务异常：用于编排链路的前置校验失败（任务不存在、无权访问、状态不可恢复等）。
 *
 * <p>与 {@link AgentWorkflowException} 的区别：本异常不绑定稳定错误码，仅携带面向调用方的消息；
 * 需要稳定错误码契约的编排失败请使用 {@link AgentWorkflowException}。
 */
public class AgentCoreException extends RuntimeException {

    public AgentCoreException(String message) {
        super(message);
    }

    public AgentCoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
