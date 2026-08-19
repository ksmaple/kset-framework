package com.kset.common.exception;

/**
 * 业务异常。
 *
 * <p>表示明确的业务规则失败（资源不存在、状态不允许等）。{@link com.kset.common.utils.retry.Retryer}
 * 默认不重试本异常（含被包装在 cause 链中的情况）。Web 侧通常映射为业务码，而不是系统 500。
 */
public class BusinessException extends RuntimeException {

    private final String errorCode;
    private final Integer code;

    public BusinessException(String message) {
        super(message);
        this.errorCode = null;
        this.code = null;
    }

    public BusinessException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.code = null;
    }

    public BusinessException(BizErrorCode errorCode) {
        this(errorCode, errorCode != null ? errorCode.message() : null);
    }

    public BusinessException(BizErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode != null ? String.valueOf(errorCode.code()) : null;
        this.code = errorCode != null ? errorCode.code() : null;
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = null;
        this.code = null;
    }

    public BusinessException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.code = null;
    }

    public BusinessException(BizErrorCode errorCode, Throwable cause) {
        this(errorCode, errorCode != null ? errorCode.message() : null, cause);
    }

    public BusinessException(BizErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode != null ? String.valueOf(errorCode.code()) : null;
        this.code = errorCode != null ? errorCode.code() : null;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public Integer getCode() {
        return code;
    }
}
