package com.kset.redis.lock.aop;

import com.kset.redis.lock.KsetRedisLock;
import com.kset.redis.lock.KsetRedisLockExecutor;
import com.kset.redis.lock.KsetRedisLockOptions;
import com.kset.redis.lock.KsetRedisLockStrategy;
import com.kset.redis.lock.annotation.KsetLocked;
import com.kset.redis.lock.internal.KsetRedissonLockProvider;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * {@link KsetLocked} 切面：委托 {@link KsetRedisLockExecutor} 加锁后执行业务方法。
 */
@Aspect
public class KsetRedisLockAspect {

    private final KsetRedisLockExecutor executor;
    private final KsetRedissonLockProvider provider;
    private final KsetRedisLockKeyResolver keyResolver = new KsetRedisLockKeyResolver();

    public KsetRedisLockAspect(KsetRedisLockExecutor executor, KsetRedissonLockProvider provider) {
        this.executor = executor;
        this.provider = provider;
    }

    @Around("@annotation(locked)")
    public Object around(ProceedingJoinPoint joinPoint, KsetLocked locked) throws Throwable {
        List<String> keys = keyResolver.resolve(joinPoint, locked);
        KsetRedisLockOptions options = toOptions(locked);
        if (options.strategy() == KsetRedisLockStrategy.OPTIONAL) {
            Optional<KsetRedisLock> acquired = keys.size() == 1
                    ? executor.tryAcquire(keys.get(0), options)
                    : executor.tryAcquireAll(keys, options);
            if (acquired.isEmpty()) {
                return null;
            }
            KsetRedisLock lock = acquired.get();
            try {
                return joinPoint.proceed();
            } finally {
                lock.unlock();
            }
        }
        KsetRedisLock lock = keys.size() == 1
                ? executor.acquire(keys.get(0), options)
                : executor.acquireAll(keys, options);
        try {
            return joinPoint.proceed();
        } finally {
            lock.unlock();
        }
    }

    private KsetRedisLockOptions toOptions(KsetLocked locked) {
        if (watchdogLease(locked.lease())) {
            return toWatchdogOptions(locked);
        }
        return toFixedLeaseOptions(locked);
    }

    /**
     * 保留原因：lease=0s 走 rejectNow(Duration.ZERO)，误传 0 会拿到 watchdog 锁。
     */
    @SuppressWarnings("unused")
    private KsetRedisLockOptions toOptionsForRollback(KsetLocked locked) {
        Duration lease = parseDuration(locked.lease(), provider.defaultLeaseTime());
        return switch (locked.strategy()) {
            case REJECT_IF_BUSY -> KsetRedisLockOptions.rejectNow(lease);
            case WAIT_THEN_FAIL -> {
                Duration wait = parseDuration(locked.waitTime(), provider.defaultWaitTime());
                yield KsetRedisLockOptions.waitThenFail(wait, lease);
            }
            case OPTIONAL -> KsetRedisLockOptions.builder()
                    .strategy(KsetRedisLockStrategy.OPTIONAL)
                    .waitTime(Duration.ZERO)
                    .leaseTime(lease)
                    .build();
            case BLOCK_UNTIL_ACQUIRED -> KsetRedisLockOptions.blockUntil(lease);
        };
    }

    private KsetRedisLockOptions toWatchdogOptions(KsetLocked locked) {
        return switch (locked.strategy()) {
            case REJECT_IF_BUSY -> KsetRedisLockOptions.rejectNowWatchdog();
            case WAIT_THEN_FAIL -> KsetRedisLockOptions.waitThenFailWatchdog(
                    parseDuration(locked.waitTime(), provider.defaultWaitTime()));
            case OPTIONAL -> KsetRedisLockOptions.builder()
                    .strategy(KsetRedisLockStrategy.OPTIONAL)
                    .waitTime(Duration.ZERO)
                    .watchdog(true)
                    .build();
            case BLOCK_UNTIL_ACQUIRED -> KsetRedisLockOptions.blockUntilWatchdog();
        };
    }

    private KsetRedisLockOptions toFixedLeaseOptions(KsetLocked locked) {
        Duration lease = parseDuration(locked.lease(), provider.defaultLeaseTime());
        return switch (locked.strategy()) {
            case REJECT_IF_BUSY -> KsetRedisLockOptions.rejectNow(lease);
            case WAIT_THEN_FAIL -> {
                Duration wait = parseDuration(locked.waitTime(), provider.defaultWaitTime());
                yield KsetRedisLockOptions.waitThenFail(wait, lease);
            }
            case OPTIONAL -> KsetRedisLockOptions.builder()
                    .strategy(KsetRedisLockStrategy.OPTIONAL)
                    .waitTime(Duration.ZERO)
                    .leaseTime(lease)
                    .build();
            case BLOCK_UNTIL_ACQUIRED -> KsetRedisLockOptions.blockUntil(lease);
        };
    }

    private static boolean watchdogLease(String raw) {
        if (!StringUtils.hasText(raw)) {
            return false;
        }
        return DurationStyle.SIMPLE.parse(raw).isZero();
    }

    private static Duration parseDuration(String raw, Duration fallback) {
        if (!StringUtils.hasText(raw)) {
            return fallback;
        }
        return DurationStyle.SIMPLE.parse(raw);
    }
}
