package com.kset.redis.lock;

import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Static facade for Redis lock operations.
 */
public final class KsetRedisLocks {

    private static volatile KsetRedisLockExecutor executor;

    private KsetRedisLocks() {
    }

    public static void bind(KsetRedisLockExecutor bound) {
        executor = bound;
    }

    public static void unbind() {
        executor = null;
    }

    public static KsetRedisLock acquire(String lockKey, KsetRedisLockOptions options) {
        return requireExecutor().acquire(lockKey, options);
    }

    public static Optional<KsetRedisLock> tryAcquire(String lockKey, KsetRedisLockOptions options) {
        return requireExecutor().tryAcquire(lockKey, options);
    }

    public static void runExclusive(String lockKey, Runnable action) {
        requireExecutor().runExclusive(lockKey, action);
    }

    public static void runWithWait(String lockKey, Duration waitTime, Runnable action) {
        requireExecutor().runWithWait(lockKey, waitTime, action);
    }

    public static void runExclusiveAll(Collection<String> lockKeys, Runnable action) {
        requireExecutor().runExclusiveAll(lockKeys, action);
    }

    public static <T> T callExclusive(String lockKey, Supplier<T> action) {
        return requireExecutor().callExclusive(lockKey, action);
    }

    private static KsetRedisLockExecutor requireExecutor() {
        KsetRedisLockExecutor e = executor;
        if (e == null) {
            throw new IllegalStateException(
                    "KsetRedisLocks is not initialized; ensure Redisson is on the classpath and kset.redis.redisson.enabled is not false");
        }
        return e;
    }
}
