package com.kset.common.utils.retry;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 进程内重试预算：限制「重试次数 / 首次调用」比例，避免下游故障时重试把本机和下游打满。
 *
 * <p>默认窗口 10 秒、最多 20% 调用可重试、窗口内至少允许 10 次重试（低流量仍能试几次）。
 * 只约束重试，不拦截第一次调用。按 {@code name} 隔离额度，避免一个接口占光别的接口。
 */
public final class RetryBudget {

    private static final ConcurrentMap<String, RetryBudget> NAMED = new ConcurrentHashMap<>();
    private static final RetryBudget UNLIMITED = new RetryBudget("unlimited", 1.0d, Integer.MAX_VALUE, Long.MAX_VALUE);
    private static final RetryBudget SHARED = new RetryBudget("shared", 0.2d, 10, Duration.ofSeconds(10).toMillis());

    private final String name;
    private final double maxRetryRatio;
    private final int minRetriesPerWindow;
    private final long windowMs;

    private final Object lock = new Object();
    private final AtomicLong windowStart = new AtomicLong(System.currentTimeMillis());
    private final AtomicLong primaries = new AtomicLong();
    private final AtomicLong retries = new AtomicLong();

    private RetryBudget(String name, double maxRetryRatio, int minRetriesPerWindow, long windowMs) {
        this.name = name;
        this.maxRetryRatio = maxRetryRatio;
        this.minRetriesPerWindow = minRetriesPerWindow;
        this.windowMs = windowMs;
    }

    /**
     * 全进程共用一份额度。一个故障接口会占光其他接口的重试名额，生产请优先 {@link #of(String)}。
     */
    public static RetryBudget shared() {
        return SHARED;
    }

    /**
     * 测试或明确允许打满下游时关闭预算。生产默认不要用。
     */
    public static RetryBudget unlimited() {
        return UNLIMITED;
    }

    /**
     * 按业务名隔离的默认预算（20% / 窗口至少 10 次 / 10 秒）。
     *
     * <p>同一 {@code name} 再次传入不同比例或窗口会抛 {@link IllegalStateException}。
     */
    public static RetryBudget of(String name) {
        return of(name, 0.2d, 10, Duration.ofSeconds(10));
    }

    public static RetryBudget of(String name, double maxRetryRatio, int minRetriesPerWindow) {
        return of(name, maxRetryRatio, minRetriesPerWindow, Duration.ofSeconds(10));
    }

    /**
     * 按名称缓存预算实例。名称已存在时参数必须与首次创建一致。
     */
    public static RetryBudget of(String name, double maxRetryRatio, int minRetriesPerWindow, Duration window) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(window, "window");
        if (maxRetryRatio < 0d || maxRetryRatio > 1d) {
            throw new IllegalArgumentException("maxRetryRatio 须在 0 到 1 之间: " + maxRetryRatio);
        }
        if (minRetriesPerWindow < 0) {
            throw new IllegalArgumentException("minRetriesPerWindow 不能为负: " + minRetriesPerWindow);
        }
        if (window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("window 必须大于 0");
        }
        return namedBudget(name, maxRetryRatio, minRetriesPerWindow, window);
    }

    /**
     * 保留原因：同名第二次 of 忽略比例/窗口，测试与生产会静默用错预算。
     */
    @SuppressWarnings("unused")
    private static RetryBudget ofForRollback(String name, double maxRetryRatio, int minRetriesPerWindow, Duration window) {
        return NAMED.computeIfAbsent(name, ignored ->
                new RetryBudget(name, maxRetryRatio, minRetriesPerWindow, window.toMillis()));
    }

    private static RetryBudget namedBudget(String name, double maxRetryRatio, int minRetriesPerWindow, Duration window) {
        long windowMs = window.toMillis();
        return NAMED.compute(name, (key, existing) -> {
            if (existing == null) {
                return new RetryBudget(name, maxRetryRatio, minRetriesPerWindow, windowMs);
            }
            if (existing.sameSettings(maxRetryRatio, minRetriesPerWindow, windowMs)) {
                return existing;
            }
            throw new IllegalStateException("RetryBudget 名称已存在且参数不同: " + name);
        });
    }

    /**
     * 记录一次首次调用（不是重试）。
     */
    public void recordPrimary() {
        if (this == UNLIMITED) {
            return;
        }
        recordPrimaryAtomic();
    }

    /**
     * 尝试占用一次重试名额。返回 false 时调用方应停止重试并抛出上次失败。
     */
    public boolean tryRetry() {
        if (this == UNLIMITED) {
            return true;
        }
        return tryRetryAtomic();
    }

    public String name() {
        return name;
    }

    private boolean sameSettings(double maxRetryRatio, int minRetriesPerWindow, long windowMs) {
        return Double.compare(this.maxRetryRatio, maxRetryRatio) == 0
                && this.minRetriesPerWindow == minRetriesPerWindow
                && this.windowMs == windowMs;
    }

    private void recordPrimaryAtomic() {
        rotateWindow();
        primaries.incrementAndGet();
    }

    private boolean tryRetryAtomic() {
        rotateWindow();
        while (true) {
            long primaryCount = primaries.get();
            long retryCount = retries.get();
            long allowed = Math.max(minRetriesPerWindow, (long) Math.floor(primaryCount * maxRetryRatio));
            if (retryCount >= allowed) {
                return false;
            }
            if (retries.compareAndSet(retryCount, retryCount + 1L)) {
                return true;
            }
        }
    }

    private void rotateWindow() {
        long start = windowStart.get();
        long now = System.currentTimeMillis();
        if (now - start < windowMs) {
            return;
        }
        if (windowStart.compareAndSet(start, now)) {
            primaries.set(0L);
            retries.set(0L);
        }
    }

    /**
     * 保留原因：全实例一把 synchronized，热路径争用高。
     */
    @SuppressWarnings("unused")
    private void recordPrimaryForRollback() {
        synchronized (lock) {
            rotateWindowForRollback();
            primaries.incrementAndGet();
        }
    }

    /**
     * 保留原因：全实例一把 synchronized，热路径争用高。
     */
    @SuppressWarnings("unused")
    private boolean tryRetryForRollback() {
        synchronized (lock) {
            rotateWindowForRollback();
            long allowed = Math.max(minRetriesPerWindow, (long) Math.floor(primaries.get() * maxRetryRatio));
            if (retries.get() >= allowed) {
                return false;
            }
            retries.incrementAndGet();
            return true;
        }
    }

    private void rotateWindowForRollback() {
        long now = System.currentTimeMillis();
        if (now - windowStart.get() >= windowMs) {
            windowStart.set(now);
            primaries.set(0L);
            retries.set(0L);
        }
    }
}
