package com.kset.redis.lock;

import com.kset.redis.lock.KsetRedisLock;
import com.kset.redis.lock.KsetRedisLockBusyException;
import com.kset.redis.lock.KsetRedisLockExecutor;
import com.kset.redis.lock.KsetRedisLockOptions;
import com.kset.redis.lock.KsetRedisLockTimeoutException;
import com.kset.redis.core.KsetRedisTtlPolicy;
import com.kset.redis.lock.internal.KsetRedissonLockProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KsetRedisLockExecutorTest {

    @Mock
    private KsetRedissonLockProvider provider;

    private KsetRedisLockExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new KsetRedisLockExecutor(provider, new KsetRedisTtlPolicy(Duration.ofMinutes(30), null));
        lenient().when(provider.defaultLeaseTime()).thenReturn(Duration.ofMinutes(5));
        lenient().when(provider.defaultWaitTime()).thenReturn(Duration.ofSeconds(3));
        lenient().when(provider.compositeKey(anyCollection())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            Iterable<String> keys = inv.getArgument(0);
            StringBuilder sb = new StringBuilder();
            for (String k : keys) {
                if (!sb.isEmpty()) {
                    sb.append('|');
                }
                sb.append(k);
            }
            return sb.toString();
        });
    }

    @Test
    void runExclusiveSucceeds() {
        KsetRedisLock lock = mock(KsetRedisLock.class);
        when(provider.tryLockAll(eq(List.of("job")), eq(Duration.ZERO), any(Duration.class)))
                .thenReturn(Optional.of(lock));

        AtomicBoolean ran = new AtomicBoolean(false);
        executor.runExclusive("job", () -> ran.set(true));

        assertTrue(ran.get());
        verify(lock).unlock();
    }

    @Test
    void runExclusiveThrowsWhenBusy() {
        when(provider.tryLockAll(eq(List.of("job")), eq(Duration.ZERO), any(Duration.class)))
                .thenReturn(Optional.empty());

        assertThrows(KsetRedisLockBusyException.class, () -> executor.runExclusive("job", () -> {
        }));
    }

    @Test
    void runWithWaitThrowsOnTimeout() {
        when(provider.tryLockAll(eq(List.of("job")), eq(Duration.ofSeconds(2)), any(Duration.class)))
                .thenReturn(Optional.empty());

        assertThrows(KsetRedisLockTimeoutException.class,
                () -> executor.runWithWait("job", Duration.ofSeconds(2), () -> {
                }));
    }

    @Test
    void acquireForCrossMethodUse() {
        KsetRedisLock lock = mock(KsetRedisLock.class);
        when(lock.lockKey()).thenReturn("order:1");
        when(provider.tryLockAll(eq(List.of("order:1")), any(), any())).thenReturn(Optional.of(lock));

        KsetRedisLock acquired = executor.acquire("order:1", KsetRedisLockOptions.rejectNow(Duration.ofMinutes(1)));
        assertEquals("order:1", acquired.lockKey());
    }

    @Test
    void runExclusiveAllUsesMultiLock() {
        KsetRedisLock lock = mock(KsetRedisLock.class);
        when(provider.tryLockAll(eq(List.of("a", "b")), eq(Duration.ZERO), any(Duration.class)))
                .thenReturn(Optional.of(lock));

        executor.runExclusiveAll(List.of("a", "b"), () -> {
        });
        verify(lock).unlock();
    }

}
