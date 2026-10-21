package com.kset.auth.web;

import com.kset.common.auth.LoginRequiredException;
import com.kset.common.auth.PermissionDeniedException;
import com.kset.web.response.ApiResponse;
import com.kset.web.config.KsetWebProperties;
import com.kset.web.handler.WebExceptionMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

public class AuthExceptionHandler implements WebExceptionMapper {

    private final KsetWebProperties.ExceptionHandling properties;

    public AuthExceptionHandler(KsetWebProperties webProperties) {
        this.properties = webProperties.getExceptionHandling();
    }

    public ResponseEntity<ApiResponse<Void>> handleLoginRequired(LoginRequiredException ex) {
        return response(HttpStatus.UNAUTHORIZED, ApiResponse.fail(401, messageOrDefault(ex.getMessage(), "未登录")));
    }

    public ResponseEntity<ApiResponse<Void>> handlePermissionDenied(PermissionDeniedException ex) {
        return response(HttpStatus.FORBIDDEN, ApiResponse.fail(403, messageOrDefault(ex.getMessage(), "无权限")));
    }

    @Override
    public boolean supports(Exception exception) {
        return exception instanceof LoginRequiredException || exception instanceof PermissionDeniedException;
    }

    @Override
    public ResponseEntity<ApiResponse<Void>> handle(Exception exception) {
        if (exception instanceof LoginRequiredException loginRequired) {
            return handleLoginRequired(loginRequired);
        }
        return handlePermissionDenied((PermissionDeniedException) exception);
    }

    private ResponseEntity<ApiResponse<Void>> response(HttpStatus status, ApiResponse<Void> body) {
        return ResponseEntity.status(properties.isUseHttpStatus() ? status : HttpStatus.OK).body(body);
    }

    private String messageOrDefault(String message, String defaultMessage) {
        return message == null || message.isBlank() ? defaultMessage : message;
    }
}
