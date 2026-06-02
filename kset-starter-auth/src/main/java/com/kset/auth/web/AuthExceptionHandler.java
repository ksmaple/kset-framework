package com.kset.auth.web;

import com.kset.common.auth.LoginRequiredException;
import com.kset.common.auth.PermissionDeniedException;
import com.kset.web.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AuthExceptionHandler {

    @ExceptionHandler(LoginRequiredException.class)
    public ResponseEntity<ApiResponse<Void>> handleLoginRequired(LoginRequiredException ex) {
        return ResponseEntity.ok(ApiResponse.fail(401, messageOrDefault(ex.getMessage(), "未登录")));
    }

    @ExceptionHandler(PermissionDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handlePermissionDenied(PermissionDeniedException ex) {
        return ResponseEntity.ok(ApiResponse.fail(403, messageOrDefault(ex.getMessage(), "无权限")));
    }

    private String messageOrDefault(String message, String defaultMessage) {
        return message == null || message.isBlank() ? defaultMessage : message;
    }
}
