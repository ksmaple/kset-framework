package com.kset.web.handler;

import com.kset.common.exception.BusinessException;
import com.kset.web.config.KsetWebProperties;
import com.kset.web.response.ApiResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private final KsetWebProperties.ExceptionHandling properties;

    public GlobalExceptionHandler(KsetWebProperties webProperties) {
        this.properties = webProperties.getExceptionHandling();
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex) {
        int code = businessCode(ex);
        return response(HttpStatus.BAD_REQUEST, ApiResponse.fail(code, messageOrDefault(ex.getMessage(), "业务处理失败")));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return response(HttpStatus.BAD_REQUEST,
                ApiResponse.fail(properties.getValidationCode(), "参数类型错误: " + ex.getName()));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParameter(MissingServletRequestParameterException ex) {
        return response(HttpStatus.BAD_REQUEST,
                ApiResponse.fail(properties.getValidationCode(), "缺少请求参数: " + ex.getParameterName()));
    }

    @ExceptionHandler(MissingPathVariableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingPathVariable(MissingPathVariableException ex) {
        return response(HttpStatus.BAD_REQUEST,
                ApiResponse.fail(properties.getValidationCode(), "缺少路径变量: " + ex.getVariableName()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.joining("; "));
        return response(HttpStatus.BAD_REQUEST,
                ApiResponse.fail(properties.getValidationCode(), messageOrDefault(message, "参数校验失败")));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return response(HttpStatus.METHOD_NOT_ALLOWED,
                ApiResponse.fail(properties.getMethodNotAllowedCode(), "请求方法不支持: " + ex.getMethod()));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        return response(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                ApiResponse.fail(properties.getUnsupportedMediaTypeCode(), "请求内容类型不支持: " + ex.getContentType()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMessageNotReadable(HttpMessageNotReadableException ex) {
        return response(HttpStatus.BAD_REQUEST,
                ApiResponse.fail(properties.getBadRequestCode(), "请求体格式错误"));
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ApiResponse<Void>> handleNotFound(Exception ex) {
        return response(HttpStatus.NOT_FOUND,
                ApiResponse.fail(properties.getNotFoundCode(), "请求资源不存在"));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<ApiResponse<Void>> handleValidationException(Exception ex) {
        String message = "参数校验失败";
        if (ex instanceof MethodArgumentNotValidException manv) {
            message = bindingMessage(manv.getBindingResult(), message);
        } else if (ex instanceof BindException be) {
            message = bindingMessage(be.getBindingResult(), message);
        }
        return response(HttpStatus.BAD_REQUEST, ApiResponse.fail(properties.getValidationCode(), message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception ex) {
        log.error("Unhandled web exception", ex);
        return response(HttpStatus.INTERNAL_SERVER_ERROR,
                ApiResponse.fail(properties.getSystemCode(), "系统异常，请稍后重试"));
    }

    private ResponseEntity<ApiResponse<Void>> response(HttpStatus status, ApiResponse<Void> body) {
        return ResponseEntity.status(properties.isUseHttpStatus() ? status : HttpStatus.OK).body(body);
    }

    private int businessCode(BusinessException ex) {
        Integer code = ex.getCode();
        if (code != null) {
            return code;
        }
        String errorCode = ex.getErrorCode();
        if (!properties.isParseBusinessErrorCode() || errorCode == null || errorCode.isBlank()) {
            return properties.getDefaultBusinessCode();
        }
        return parseErrorCode(errorCode, properties.getDefaultBusinessCode());
    }

    private String messageOrDefault(String message, String defaultMessage) {
        return message == null || message.isBlank() ? defaultMessage : message;
    }

    private String bindingMessage(BindingResult bindingResult, String defaultMessage) {
        String message = bindingResult.getAllErrors().stream()
                .map(error -> messageOrDefault(error.getDefaultMessage(), null))
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.joining("; "));
        return messageOrDefault(message, defaultMessage);
    }

    private int parseErrorCode(String errorCode, int defaultCode) {
        try {
            return Integer.parseInt(errorCode);
        } catch (NumberFormatException e) {
            return defaultCode;
        }
    }
}
