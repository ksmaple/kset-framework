package com.kset.redis.web;

import com.kset.redis.lock.KsetRedisLockBusyException;
import com.kset.redis.lock.KsetRedisLockInterruptedException;
import com.kset.redis.lock.KsetRedisLockTimeoutException;
import com.kset.web.config.KsetWebProperties;
import com.kset.web.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class KsetRedisLockExceptionHandlerTest {

    private final KsetRedisLockExceptionHandler handler = new KsetRedisLockExceptionHandler(new KsetWebProperties());

    @Test
    void busyMapsTo409WithoutLockKey() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleBusy(new KsetRedisLockBusyException("order:secret"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(409);
        assertThat(response.getBody().getMessage()).isEqualTo("锁繁忙，请稍后重试");
        assertThat(response.getBody().getMessage()).doesNotContain("order:secret");
    }

    @Test
    void timeoutMapsTo408WithoutLockKey() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleTimeout(new KsetRedisLockTimeoutException("order:secret", Duration.ofSeconds(3)));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(408);
        assertThat(response.getBody().getMessage()).doesNotContain("order:secret");
    }

    @Test
    void interruptMapsTo503WithoutLockKey() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleInterrupted(new KsetRedisLockInterruptedException("order:secret", new InterruptedException()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(503);
        assertThat(response.getBody().getMessage()).isEqualTo("操作已取消");
        assertThat(response.getBody().getMessage()).doesNotContain("order:secret");
    }

    @Test
    void canUseRealHttpStatusWhenConfigured() {
        KsetWebProperties properties = new KsetWebProperties();
        properties.getExceptionHandling().setUseHttpStatus(true);
        KsetRedisLockExceptionHandler realStatusHandler = new KsetRedisLockExceptionHandler(properties);

        ResponseEntity<ApiResponse<Void>> response =
                realStatusHandler.handleBusy(new KsetRedisLockBusyException("order:1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(409);
    }
}
