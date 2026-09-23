package com.kset.agent.core;

import com.kset.agent.dto.AgentWorkflowErrorCode;

/** 核心编排内部异常，保留稳定错误码并隐藏供应方异常明细。 */
public class AgentWorkflowException extends RuntimeException {

    private final AgentWorkflowErrorCode errorCode;

    public AgentWorkflowException(AgentWorkflowErrorCode errorCode) {
        super(errorCode.defaultMessage());
        this.errorCode = errorCode;
    }

    public AgentWorkflowException(AgentWorkflowErrorCode errorCode, Throwable cause) {
        super(errorCode.defaultMessage(), cause);
        this.errorCode = errorCode;
    }

    public AgentWorkflowErrorCode getErrorCode() {
        return errorCode;
    }
}
