package com.kset.redis.web;

import com.kset.redis.lock.KsetRedisLockBusyException;
import com.kset.redis.lock.KsetRedisLockInterruptedException;
import com.kset.redis.lock.KsetRedisLockTimeoutException;
import com.kset.web.config.KsetWebProperties;
import com.kset.web.response.ApiResponse;
import com.kset.web.handler.WebExceptionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Web mapping for Redis lock contention. Response body never includes lockKey.
 */
public class KsetRedisLockExceptionHandler implements WebExceptionMapper {

    private static final Logger log = LoggerFactory.getLogger(KsetRedisLockExceptionHandler.class);

    private final KsetWebProperties.ExceptionHandling properties;

    public KsetRedisLockExceptionHandler(KsetWebProperties webProperties) {
        this.properties = webProperties != null
                ? webProperties.getExceptionHandling()
                : new KsetWebProperties().getExceptionHandling();
    }

    public ResponseEntity<ApiResponse<Void>> handleBusy(KsetRedisLockBusyException ex) {
        log.warn("redis lock busy lockKey={}", ex.lockKey());
        return response(HttpStatus.CONFLICT, ApiResponse.fail(409, "锁繁忙，请稍后重试"));
    }

    public ResponseEntity<ApiResponse<Void>> handleTimeout(KsetRedisLockTimeoutException ex) {
        log.warn("redis lock timeout lockKey={}", ex.lockKey());
        return response(HttpStatus.REQUEST_TIMEOUT, ApiResponse.fail(408, "获取锁超时，请稍后重试"));
    }

    public ResponseEntity<ApiResponse<Void>> handleInterrupted(KsetRedisLockInterruptedException ex) {
        log.warn("redis lock interrupted lockKey={}", ex.lockKey());
        return response(HttpStatus.SERVICE_UNAVAILABLE, ApiResponse.fail(503, "操作已取消"));
    }

    @Override
    public boolean supports(Exception exception) {
        return exception instanceof KsetRedisLockBusyException
                || exception instanceof KsetRedisLockTimeoutException
                || exception instanceof KsetRedisLockInterruptedException;
    }

    @Override
    public ResponseEntity<ApiResponse<Void>> handle(Exception exception) {
        if (exception instanceof KsetRedisLockBusyException busy) {
            return handleBusy(busy);
        }
        if (exception instanceof KsetRedisLockTimeoutException timeout) {
            return handleTimeout(timeout);
        }
        return handleInterrupted((KsetRedisLockInterruptedException) exception);
    }

    private ResponseEntity<ApiResponse<Void>> response(HttpStatus status, ApiResponse<Void> body) {
        return ResponseEntity.status(properties.isUseHttpStatus() ? status : HttpStatus.OK).body(body);
    }
}
