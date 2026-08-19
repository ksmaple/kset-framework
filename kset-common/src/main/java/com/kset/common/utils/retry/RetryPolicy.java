package com.kset.common.utils.retry;

import com.kset.common.exception.BusinessException;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 重试策略。默认最多 3 次、指数退避 + 全抖动、总时长 5 秒、带名为 {@code default} 的重试预算。
 *
 * <p>生产请用 {@link #named(String)} 按业务隔离额度，避免一个接口占光其他接口的重试名额。
 * {@link BusinessException}、中断、参数错误、线程池拒绝默认不重试（沿 cause 链识别），避免把业务失败或过载放大。
 */
public final class RetryPolicy {

    private static final Duration DEFAULT_INITIAL = Duration.ofMillis(100);
    private static final Duration DEFAULT_MAX_BACKOFF = Duration.ofSeconds(2);
    private static final Duration DEFAULT_MAX_ELAPSED = Duration.ofSeconds(5);

    private final int maxAttempts;
    private final boolean exponential;
    private final Duration initialBackoff;
    private final Duration maxBackoff;
    private final boolean jitter;
    private final Duration maxElapsed;
    private final Set<Class<? extends Throwable>> retryOn;
    private final Set<Class<? extends Throwable>> notRetryOn;
    private final RetryBudget budget;

    private RetryPolicy(int maxAttempts,
                        boolean exponential,
                        Duration initialBackoff,
                        Duration maxBackoff,
                        boolean jitter,
                        Duration maxElapsed,
                        Set<Class<? extends Throwable>> retryOn,
                        Set<Class<? extends Throwable>> notRetryOn,
                        RetryBudget budget) {
        this.maxAttempts = maxAttempts;
        this.exponential = exponential;
        this.initialBackoff = initialBackoff;
        this.maxBackoff = maxBackoff;
        this.jitter = jitter;
        this.maxElapsed = maxElapsed;
        this.retryOn = retryOn;
        this.notRetryOn = notRetryOn;
        this.budget = budget;
    }

    public static RetryPolicy defaults() {
        return new RetryPolicy(
                3,
                true,
                DEFAULT_INITIAL,
                DEFAULT_MAX_BACKOFF,
                true,
                DEFAULT_MAX_ELAPSED,
                Set.of(),
                defaultNotRetryOn(),
                RetryBudget.of("default"));
    }

    /**
     * 按业务名隔离重试预算。生产请用本方法，不要用 {@link #defaults()}。
     */
    public static RetryPolicy named(String name) {
        Objects.requireNonNull(name, "name");
        RetryPolicy base = defaults();
        return base.budget(RetryBudget.of(name));
    }

    public static RetryPolicy maxAttempts(int maxAttempts) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts 至少为 1: " + maxAttempts);
        }
        RetryPolicy base = defaults();
        return base.copy(maxAttempts, base.exponential, base.initialBackoff, base.maxBackoff, base.jitter,
                base.maxElapsed, base.retryOn, base.notRetryOn, base.budget);
    }

    public RetryPolicy fixed(Duration delay) {
        Objects.requireNonNull(delay, "delay");
        requirePositive(delay, "delay");
        return copy(maxAttempts, false, delay, delay, jitter, maxElapsed, retryOn, notRetryOn, budget);
    }

    public RetryPolicy exponential(Duration initial, Duration max) {
        Objects.requireNonNull(initial, "initial");
        Objects.requireNonNull(max, "max");
        requirePositive(initial, "initial");
        requirePositive(max, "max");
        if (max.compareTo(initial) < 0) {
            throw new IllegalArgumentException("max 不能小于 initial");
        }
        return copy(maxAttempts, true, initial, max, jitter, maxElapsed, retryOn, notRetryOn, budget);
    }

    public RetryPolicy jitter() {
        return copy(maxAttempts, exponential, initialBackoff, maxBackoff, true, maxElapsed, retryOn, notRetryOn, budget);
    }

    public RetryPolicy withoutJitter() {
        return copy(maxAttempts, exponential, initialBackoff, maxBackoff, false, maxElapsed, retryOn, notRetryOn, budget);
    }

    public RetryPolicy maxElapsed(Duration maxElapsed) {
        Objects.requireNonNull(maxElapsed, "maxElapsed");
        requirePositive(maxElapsed, "maxElapsed");
        return copy(maxAttempts, exponential, initialBackoff, maxBackoff, jitter, maxElapsed, retryOn, notRetryOn, budget);
    }

    @SafeVarargs
    public final RetryPolicy retryOn(Class<? extends Throwable>... types) {
        if (types == null || types.length == 0) {
            throw new IllegalArgumentException("retryOn 不能为空");
        }
        return copy(maxAttempts, exponential, initialBackoff, maxBackoff, jitter, maxElapsed,
                Set.copyOf(Arrays.asList(types)), notRetryOn, budget);
    }

    public RetryPolicy budget(RetryBudget budget) {
        Objects.requireNonNull(budget, "budget");
        return copy(maxAttempts, exponential, initialBackoff, maxBackoff, jitter, maxElapsed, retryOn, notRetryOn, budget);
    }

    /**
     * 关闭预算。仅用于单测或明确可接受重试打满的场景。
     */
    public RetryPolicy noBudget() {
        return budget(RetryBudget.unlimited());
    }

    int maxAttempts() {
        return maxAttempts;
    }

    Duration maxElapsed() {
        return maxElapsed;
    }

    RetryBudget budget() {
        return budget;
    }

    boolean shouldRetry(Throwable error) {
        return shouldRetryByCause(error);
    }

    /**
     * 保留原因：只匹配最外层异常，ExecutionException 包装 BusinessException 仍会重试。
     */
    @SuppressWarnings("unused")
    private boolean shouldRetryForRollback(Throwable error) {
        if (error == null || error instanceof Error) {
            return false;
        }
        if (matches(notRetryOn, error)) {
            return false;
        }
        if (retryOn.isEmpty()) {
            return true;
        }
        return matches(retryOn, error);
    }

    private boolean shouldRetryByCause(Throwable error) {
        if (error == null || error instanceof Error) {
            return false;
        }
        if (matchesCauseChain(notRetryOn, error)) {
            return false;
        }
        if (retryOn.isEmpty()) {
            return true;
        }
        return matchesCauseChain(retryOn, error);
    }

    private static boolean matchesCauseChain(Set<Class<? extends Throwable>> types, Throwable error) {
        Throwable current = error;
        int depth = 0;
        while (current != null && depth < 16) {
            if (current instanceof Error) {
                return matches(types, current);
            }
            if (matches(types, current)) {
                return true;
            }
            current = current.getCause();
            depth++;
        }
        return false;
    }

    long backoffMillis(int retryNumber) {
        long base;
        if (!exponential) {
            base = initialBackoff.toMillis();
        } else {
            int shift = Math.min(Math.max(retryNumber - 1, 0), 16);
            long grown = initialBackoff.toMillis() * (1L << shift);
            base = Math.min(grown, maxBackoff.toMillis());
        }
        if (base <= 0L) {
            return 0L;
        }
        if (!jitter) {
            return base;
        }
        return ThreadLocalRandom.current().nextLong(0L, base + 1L);
    }

    private static Set<Class<? extends Throwable>> defaultNotRetryOn() {
        Set<Class<? extends Throwable>> types = new LinkedHashSet<>();
        types.add(BusinessException.class);
        types.add(InterruptedException.class);
        types.add(IllegalArgumentException.class);
        types.add(NullPointerException.class);
        types.add(RejectedExecutionException.class);
        return Set.copyOf(types);
    }

    private static boolean matches(Set<Class<? extends Throwable>> types, Throwable error) {
        for (Class<? extends Throwable> type : types) {
            if (type.isInstance(error)) {
                return true;
            }
        }
        return false;
    }

    private static void requirePositive(Duration duration, String name) {
        if (duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException(name + " 必须大于 0");
        }
    }

    private RetryPolicy copy(int maxAttempts,
                             boolean exponential,
                             Duration initialBackoff,
                             Duration maxBackoff,
                             boolean jitter,
                             Duration maxElapsed,
                             Set<Class<? extends Throwable>> retryOn,
                             Set<Class<? extends Throwable>> notRetryOn,
                             RetryBudget budget) {
        return new RetryPolicy(maxAttempts, exponential, initialBackoff, maxBackoff, jitter, maxElapsed,
                retryOn, notRetryOn, budget);
    }
}
