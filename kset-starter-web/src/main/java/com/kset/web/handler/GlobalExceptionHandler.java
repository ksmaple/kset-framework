package com.kset.web.handler;

import com.kset.common.exception.BusinessException;
import com.kset.web.config.KsetWebProperties;
import com.kset.web.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 全局异常处理
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final KsetWebProperties.ExceptionHandling properties;

    public GlobalExceptionHandler(KsetWebProperties webProperties) {
        this.properties = webProperties.getExceptionHandling();
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex) {
        int code = businessCode(ex);
        return response(HttpStatus.BAD_REQUEST, ApiResponse.fail(code, ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return response(HttpStatus.BAD_REQUEST,
                ApiResponse.fail(properties.getValidationCode(), "参数类型错误: " + ex.getName()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return response(HttpStatus.METHOD_NOT_ALLOWED, ApiResponse.fail(405, "请求方法不支持: " + ex.getMethod()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<ApiResponse<Void>> handleValidationException(Exception ex) {
        String message = "参数校验失败";
        if (ex instanceof MethodArgumentNotValidException manv) {
            if (manv.getBindingResult().getFieldError() != null) {
                message = manv.getBindingResult().getFieldError().getDefaultMessage();
            }
        } else if (ex instanceof BindException be) {
            if (be.getBindingResult().getFieldError() != null) {
                message = be.getBindingResult().getFieldError().getDefaultMessage();
            }
        }
        return response(HttpStatus.BAD_REQUEST, ApiResponse.fail(properties.getValidationCode(), message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception ex) {
        return response(HttpStatus.INTERNAL_SERVER_ERROR,
                ApiResponse.fail(properties.getSystemCode(), "系统异常，请稍后重试"));
    }

    private ResponseEntity<ApiResponse<Void>> response(HttpStatus status, ApiResponse<Void> body) {
        return ResponseEntity.status(properties.isUseHttpStatus() ? status : HttpStatus.OK).body(body);
    }

    private int businessCode(BusinessException ex) {
        String errorCode = ex.getErrorCode();
        if (!properties.isParseBusinessErrorCode() || errorCode == null || errorCode.isBlank()) {
            return properties.getDefaultBusinessCode();
        }
        return parseErrorCode(errorCode, properties.getDefaultBusinessCode());
    }

    private int parseErrorCode(String errorCode, int defaultCode) {
        try {
            return Integer.parseInt(errorCode);
        } catch (NumberFormatException e) {
            return defaultCode;
        }
    }
}
