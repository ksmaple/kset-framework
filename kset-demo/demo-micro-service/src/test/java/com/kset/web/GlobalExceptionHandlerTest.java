package com.kset.web;

import com.kset.common.exception.BusinessException;
import com.kset.web.config.KsetWebProperties;
import com.kset.web.handler.GlobalExceptionHandler;
import com.kset.web.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(new KsetWebProperties());

    @Test
    void parsesNumericBusinessErrorCode() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleBusinessException(new BusinessException("1001", "name exists"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(1001);
        assertThat(response.getBody().getMessage()).isEqualTo("name exists");
    }

    @Test
    void handlesMissingRequestParameter() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleMissingParameter(new MissingServletRequestParameterException("userId", "Long"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(400);
        assertThat(response.getBody().getMessage()).isEqualTo("缺少请求参数: userId");
    }

    @Test
    void handlesEmptyConstraintViolationMessage() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleConstraintViolation(new ConstraintViolationException(Set.of()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("参数校验失败");
    }

    @Test
    void usesCodeForMethodNotAllowed() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleMethodNotSupported(new HttpRequestMethodNotSupportedException("PUT"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(405);
        assertThat(response.getBody().getMessage()).isEqualTo("请求方法不支持: PUT");
    }

    @Test
    void canUseRealHttpStatusWhenConfigured() {
        KsetWebProperties properties = new KsetWebProperties();
        properties.getExceptionHandling().setUseHttpStatus(true);
        GlobalExceptionHandler realStatusHandler = new GlobalExceptionHandler(properties);

        ResponseEntity<ApiResponse<Void>> response =
                realStatusHandler.handleBusinessException(new BusinessException("name exists"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(400);
    }
}
