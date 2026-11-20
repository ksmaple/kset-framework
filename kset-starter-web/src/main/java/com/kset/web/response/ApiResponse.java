package com.kset.web.response;

/**
 * Servlet 侧响应体。字段与 {@link com.kset.common.response.ApiResponse} 相同，静态方法仍返回本类型。
 */
public class ApiResponse<T> extends com.kset.common.response.ApiResponse<T> {

    public ApiResponse() {
    }

    public ApiResponse(int code, String message, T data) {
        super(code, message, data);
    }

    public ApiResponse(int code, String message, T data, String traceId) {
        super(code, message, data, traceId);
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(0, "success", data);
    }

    public static <T> ApiResponse<T> success() {
        return success(null);
    }

    public static <T> ApiResponse<T> fail(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }

    public static <T> ApiResponse<T> fail(String message) {
        return fail(-1, message);
    }
}
