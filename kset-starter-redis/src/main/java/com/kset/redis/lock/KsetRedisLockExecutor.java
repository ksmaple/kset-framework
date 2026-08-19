package com.kset.redis.lock;

import com.kset.redis.core.KsetRedisTtlPolicy;
import com.kset.redis.lock.internal.KsetRedissonLockProvider;
import com.kset.redis.monitor.KsetRedisMonitor;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Redis 分布式锁执行器（Redisson）。
 *
 * <p>默认租约 30 秒且固定 lease 不会自动续期。长任务用 {@link KsetRedisLockOptions#rejectNowWatchdog()}
 * 或 {@code @KsetLocked(lease = "0s")}。{@link KsetRedisLockOptions#rejectNow(Duration)} 传入 {@link Duration#ZERO}
 * 会被拒绝。用法见 {@code docs/usage/redis-lock.md}。
 */
public class KsetRedisLockExecutor {

    private final KsetRedissonLockProvider provider;
    private final KsetRedisTtlPolicy ttlPolicy;
    private final boolean monitorEnabled;

    public KsetRedisLockExecutor(KsetRedissonLockProvider provider, KsetRedisTtlPolicy ttlPolicy) {
        this(provider, ttlPolicy, false);
    }

    public KsetRedisLockExecutor(KsetRedissonLockProvider provider,
                                 KsetRedisTtlPolicy ttlPolicy,
                                 boolean monitorEnabled) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.ttlPolicy = Objects.requireNonNull(ttlPolicy, "ttlPolicy");
        this.monitorEnabled = monitorEnabled;
    }

    public Optional<KsetRedisLock> tryAcquire(String lockKey, KsetRedisLockOptions options) {
        return resolveAcquire(List.of(lockKey), options, false);
    }

    public Optional<KsetRedisLock> tryAcquireAll(Collection<String> lockKeys, KsetRedisLockOptions options) {
        return resolveAcquireAll(lockKeys, options, false);
    }

    public KsetRedisLock acquire(String lockKey, KsetRedisLockOptions options) {
        return tryAcquire(lockKey, options).orElseThrow(() -> toAcquireException(lockKey, options));
    }

    public KsetRedisLock acquireAll(Collection<String> lockKeys, KsetRedisLockOptions options) {
        String composite = provider.compositeKey(lockKeys);
        return tryAcquireAll(lockKeys, options)
                .orElseThrow(() -> toAcquireException(composite, options));
    }

    /**
     * 立即抢锁，失败抛 {@link KsetRedisLockBusyException}。租约用全局默认（通常 30 秒）。
     */
    public void runExclusive(String lockKey, Runnable action) {
        runExclusive(lockKey, provider.defaultLeaseTime(), action);
    }

    public void runExclusive(String lockKey, Duration leaseTime, Runnable action) {
        run(lockKey, KsetRedisLockOptions.rejectNow(leaseTime), action);
    }

    /**
     * 等待 {@code waitTime} 后仍抢不到则抛 {@link KsetRedisLockTimeoutException}。
     */
    public void runWithWait(String lockKey, Duration waitTime, Runnable action) {
        runWithWait(lockKey, waitTime, provider.defaultLeaseTime(), action);
    }

    public void runWithWait(String lockKey, Duration waitTime, Duration leaseTime, Runnable action) {
        run(lockKey, KsetRedisLockOptions.waitThenFail(waitTime, leaseTime), action);
    }

    public <T> T callExclusive(String lockKey, Supplier<T> action) {
        return callExclusive(lockKey, provider.defaultLeaseTime(), action);
    }

    public <T> T callExclusive(String lockKey, Duration leaseTime, Supplier<T> action) {
        return call(lockKey, KsetRedisLockOptions.rejectNow(leaseTime), action);
    }

    /**
     * 抢不到则跳过，返回 {@code null}，不抛异常。
     */
    public <T> T callIfLock(String lockKey, Supplier<T> action) {
        return callIfLock(lockKey, provider.defaultLeaseTime(), action);
    }

    public <T> T callIfLock(String lockKey, Duration leaseTime, Supplier<T> action) {
        return execute(lockKey, KsetRedisLockOptions.builder()
                .leaseTime(leaseTime)
                .strategy(KsetRedisLockStrategy.OPTIONAL)
                .waitTime(Duration.ZERO)
                .build(), action).orElse(null);
    }

    /**
     * 一直等到拿到锁。线程被中断抛 {@link KsetRedisLockInterruptedException}。
     */
    public void runBlocking(String lockKey, Runnable action) {
        runBlocking(lockKey, provider.defaultLeaseTime(), action);
    }

    public void runBlocking(String lockKey, Duration leaseTime, Runnable action) {
        run(lockKey, KsetRedisLockOptions.blockUntil(leaseTime), action);
    }

    public void runExclusiveAll(Collection<String> lockKeys, Runnable action) {
        runExclusiveAll(lockKeys, provider.defaultLeaseTime(), action);
    }

    public void runExclusiveAll(Collection<String> lockKeys, Duration leaseTime, Runnable action) {
        runAll(lockKeys, KsetRedisLockOptions.rejectNow(leaseTime), action);
    }

    public void runWithWaitAll(Collection<String> lockKeys, Duration waitTime, Duration leaseTime, Runnable action) {
        runAll(lockKeys, KsetRedisLockOptions.waitThenFail(waitTime, leaseTime), action);
    }

    public <T> T callExclusiveAll(Collection<String> lockKeys, Supplier<T> action) {
        return callExclusiveAll(lockKeys, provider.defaultLeaseTime(), action);
    }

    public <T> T callExclusiveAll(Collection<String> lockKeys, Duration leaseTime, Supplier<T> action) {
        return callAll(lockKeys, KsetRedisLockOptions.rejectNow(leaseTime), action);
    }

    public void run(String lockKey, KsetRedisLockOptions options, Runnable action) {
        if (monitorEnabled) {
            KsetRedisMonitor.run("redis-lock", "run", () -> doRun(lockKey, options, action));
            return;
        }
        doRun(lockKey, options, action);
    }

    private void doRun(String lockKey, KsetRedisLockOptions options, Runnable action) {
        KsetRedisLock lock = acquire(lockKey, options);
        try {
            action.run();
        } finally {
            lock.unlock();
        }
    }

    public <T> T call(String lockKey, KsetRedisLockOptions options, Supplier<T> action) {
        if (monitorEnabled) {
            try {
                return KsetRedisMonitor.call("redis-lock", "call", () -> doCall(lockKey, options, action));
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
        return doCall(lockKey, options, action);
    }

    private <T> T doCall(String lockKey, KsetRedisLockOptions options, Supplier<T> action) {
        KsetRedisLock lock = acquire(lockKey, options);
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    public void runAll(Collection<String> lockKeys, KsetRedisLockOptions options, Runnable action) {
        if (monitorEnabled) {
            KsetRedisMonitor.run("redis-lock", "runAll", () -> doRunAll(lockKeys, options, action));
            return;
        }
        doRunAll(lockKeys, options, action);
    }

    private void doRunAll(Collection<String> lockKeys, KsetRedisLockOptions options, Runnable action) {
        KsetRedisLock lock = acquireAll(lockKeys, options);
        try {
            action.run();
        } finally {
            lock.unlock();
        }
    }

    public <T> T callAll(Collection<String> lockKeys, KsetRedisLockOptions options, Supplier<T> action) {
        if (monitorEnabled) {
            try {
                return KsetRedisMonitor.call("redis-lock", "callAll", () -> doCallAll(lockKeys, options, action));
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
        return doCallAll(lockKeys, options, action);
    }

    private <T> T doCallAll(Collection<String> lockKeys, KsetRedisLockOptions options, Supplier<T> action) {
        KsetRedisLock lock = acquireAll(lockKeys, options);
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    public <T> Optional<T> execute(String lockKey, KsetRedisLockOptions options, Supplier<T> action) {
        if (monitorEnabled) {
            try {
                return KsetRedisMonitor.call("redis-lock", "execute", () -> doExecute(lockKey, options, action));
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
        return doExecute(lockKey, options, action);
    }

    private <T> Optional<T> doExecute(String lockKey, KsetRedisLockOptions options, Supplier<T> action) {
        return resolveAcquire(List.of(lockKey), options, true).flatMap(lock -> {
            try {
                return Optional.ofNullable(action.get());
            } finally {
                lock.unlock();
            }
        });
    }

    public KsetRedissonLockProvider provider() {
        return provider;
    }

    private Optional<KsetRedisLock> resolveAcquire(List<String> keys,
                                                   KsetRedisLockOptions options,
                                                   boolean allowOptional) {
        if (options.strategy() == KsetRedisLockStrategy.BLOCK_UNTIL_ACQUIRED) {
            if (keys.size() == 1) {
                return Optional.of(provider.lockBlocking(keys.get(0), resolveLease(options)));
            }
            return Optional.of(provider.lockBlockingAll(keys, resolveLease(options)));
        }
        Optional<KsetRedisLock> acquired = provider.tryLockAll(keys, resolveWait(options), resolveLease(options));
        if (acquired.isPresent()) {
            return acquired;
        }
        if (options.strategy() == KsetRedisLockStrategy.OPTIONAL) {
            return Optional.empty();
        }
        if (!allowOptional) {
            throw toAcquireException(provider.compositeKey(keys), options);
        }
        return Optional.empty();
    }

    private Optional<KsetRedisLock> resolveAcquireAll(Collection<String> lockKeys,
                                                      KsetRedisLockOptions options,
                                                      boolean allowOptional) {
        List<String> keys = lockKeys instanceof List<String> list ? list : List.copyOf(lockKeys);
        if (options.strategy() == KsetRedisLockStrategy.BLOCK_UNTIL_ACQUIRED) {
            return Optional.of(provider.lockBlockingAll(keys, resolveLease(options)));
        }
        Optional<KsetRedisLock> acquired = provider.tryLockAll(keys, resolveWait(options), resolveLease(options));
        if (acquired.isPresent()) {
            return acquired;
        }
        if (options.strategy() == KsetRedisLockStrategy.OPTIONAL) {
            return Optional.empty();
        }
        if (!allowOptional) {
            throw toAcquireException(provider.compositeKey(keys), options);
        }
        return Optional.empty();
    }

    private Duration resolveWait(KsetRedisLockOptions options) {
        if (options.strategy() == KsetRedisLockStrategy.REJECT_IF_BUSY
                || options.strategy() == KsetRedisLockStrategy.OPTIONAL) {
            return Duration.ZERO;
        }
        return options.waitTime() != null ? options.waitTime() : provider.defaultWaitTime();
    }

    private Duration resolveLease(KsetRedisLockOptions options) {
        return resolveLeaseOrWatchdog(options);
    }

    private Duration resolveLeaseOrWatchdog(KsetRedisLockOptions options) {
        if (options.watchdog()) {
            return Duration.ZERO;
        }
        return ttlPolicy.requireTtl(options.leaseTime() != null ? options.leaseTime() : provider.defaultLeaseTime());
    }

    /**
     * 保留原因：lease=0 走 requireTtl 直接拒绝，长任务无法选用 watchdog。
     */
    @SuppressWarnings("unused")
    private Duration resolveLeaseForRollback(KsetRedisLockOptions options) {
        return ttlPolicy.requireTtl(options.leaseTime() != null ? options.leaseTime() : provider.defaultLeaseTime());
    }

    private RuntimeException toAcquireException(String lockKey, KsetRedisLockOptions options) {
        if (options.strategy() == KsetRedisLockStrategy.WAIT_THEN_FAIL) {
            Duration wait = options.waitTime() != null ? options.waitTime() : provider.defaultWaitTime();
            return new KsetRedisLockTimeoutException(lockKey, wait);
        }
        return new KsetRedisLockBusyException(lockKey);
    }
}
