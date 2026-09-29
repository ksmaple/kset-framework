package com.kset.agent.core.tool;

/**
 * 工具可预期失败，携带稳定错误码供编排层记录和判断。
 */
public class ToolExecutionException extends RuntimeException {

    private final String errorCode;

    public ToolExecutionException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ToolExecutionException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
