package com.kset.redis.web;

import com.kset.redis.lock.KsetRedisLockBusyException;
import com.kset.redis.lock.KsetRedisLockInterruptedException;
import com.kset.redis.lock.KsetRedisLockTimeoutException;
import com.kset.web.config.KsetWebProperties;
import com.kset.web.response.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Web mapping for Redis lock contention. Response body never includes lockKey.
 */
@RestControllerAdvice
public class KsetRedisLockExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(KsetRedisLockExceptionHandler.class);

    private final KsetWebProperties.ExceptionHandling properties;

    public KsetRedisLockExceptionHandler(KsetWebProperties webProperties) {
        this.properties = webProperties != null
                ? webProperties.getExceptionHandling()
                : new KsetWebProperties().getExceptionHandling();
    }

    @ExceptionHandler(KsetRedisLockBusyException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusy(KsetRedisLockBusyException ex) {
        log.warn("redis lock busy lockKey={}", ex.lockKey());
        return response(HttpStatus.CONFLICT, ApiResponse.fail(409, "锁繁忙，请稍后重试"));
    }

    @ExceptionHandler(KsetRedisLockTimeoutException.class)
    public ResponseEntity<ApiResponse<Void>> handleTimeout(KsetRedisLockTimeoutException ex) {
        log.warn("redis lock timeout lockKey={}", ex.lockKey());
        return response(HttpStatus.REQUEST_TIMEOUT, ApiResponse.fail(408, "获取锁超时，请稍后重试"));
    }

    @ExceptionHandler(KsetRedisLockInterruptedException.class)
    public ResponseEntity<ApiResponse<Void>> handleInterrupted(KsetRedisLockInterruptedException ex) {
        log.warn("redis lock interrupted lockKey={}", ex.lockKey());
        return response(HttpStatus.SERVICE_UNAVAILABLE, ApiResponse.fail(503, "操作已取消"));
    }

    private ResponseEntity<ApiResponse<Void>> response(HttpStatus status, ApiResponse<Void> body) {
        return ResponseEntity.status(properties.isUseHttpStatus() ? status : HttpStatus.OK).body(body);
    }
}
