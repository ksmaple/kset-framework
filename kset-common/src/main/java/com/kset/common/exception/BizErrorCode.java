package com.kset.common.exception;

/**
 * 可扩展业务错误码契约。
 *
 * <p>业务侧可通过枚举实现该接口，并传入 {@link BusinessException} 统一交给 Web 异常处理。</p>
 */
public interface BizErrorCode {

    /**
     * 业务错误码。
     */
    int code();

    /**
     * 默认错误提示。
     */
    String message();
}
