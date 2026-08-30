package com.kset.redis.lock.internal;

import com.kset.redis.config.KsetRedisProperties;
import com.kset.redis.core.KsetRedisTtlPolicy;
import com.kset.redis.lock.KsetRedisLockInterruptedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KsetRedissonLockProviderTest {

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock rLock;

    @AfterEach
    void clearInterrupt() {
        Thread.interrupted();
    }

    @Test
    void interruptThrowsInsteadOfEmpty() throws InterruptedException {
        when(redissonClient.getLock("job")).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS)))
                .thenThrow(new InterruptedException("cancelled"));

        KsetRedissonLockProvider provider = new KsetRedissonLockProvider(
                redissonClient,
                new KsetRedisProperties(),
                new KsetRedisTtlPolicy(Duration.ofMinutes(30), null));

        assertThrows(KsetRedisLockInterruptedException.class,
                () -> provider.tryLockAll(List.of("job"), Duration.ZERO, Duration.ofSeconds(30)));
        assertTrue(Thread.currentThread().isInterrupted());
    }

    @Test
    void watchdogUsesNegativeLease() throws InterruptedException {
        when(redissonClient.getLock("job")).thenReturn(rLock);
        when(rLock.tryLock(eq(0L), eq(-1L), eq(TimeUnit.MILLISECONDS))).thenReturn(true);

        KsetRedissonLockProvider provider = new KsetRedissonLockProvider(
                redissonClient,
                new KsetRedisProperties(),
                new KsetRedisTtlPolicy(Duration.ofMinutes(30), null));

        assertTrue(provider.tryLockAll(List.of("job"), Duration.ZERO, Duration.ZERO).isPresent());
        verify(rLock).tryLock(0L, -1L, TimeUnit.MILLISECONDS);
    }
}
