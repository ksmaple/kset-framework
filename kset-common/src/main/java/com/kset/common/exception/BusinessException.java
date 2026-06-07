package com.kset.common.exception;

/**
 * 业务异常
 *
 * <p>用于表达明确的业务规则违反，如"资源不存在"、"用户已禁用"等。
 * 区别于系统异常（RuntimeException），业务异常有明确的错误消息，通常不需要完整堆栈。
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

    public String getErrorCode() {
        return errorCode;
    }

    public Integer getCode() {
        return code;
    }
}
