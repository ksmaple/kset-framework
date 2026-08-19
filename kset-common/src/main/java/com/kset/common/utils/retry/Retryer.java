package com.kset.common.utils.retry;

import com.kset.common.exception.UncheckedInterruptedException;
import com.kset.common.exception.UncheckedTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * 同步重试。第一次失败后才进入预算、退避；预算用尽或不可重试异常立即失败，避免重试把系统打满。
 *
 * <p>生产请带业务名：{@code Retryer.call("order-http", action)}，按接口隔离预算。
 * 未命名调用共用 {@code default} 预算。用法见 {@code docs/usage/retry.md}。
 */
public final class Retryer {

    private static final Logger log = LoggerFactory.getLogger(Retryer.class);

    private Retryer() {
    }

    /**
     * 使用默认策略重试。全进程共用名为 {@code default} 的预算，生产请改用 {@link #call(String, Callable)}。
     */
    public static <T> T call(Callable<T> action) {
        return call(action, RetryPolicy.defaults());
    }

    /**
     * 使用按业务名隔离的默认策略重试。
     *
     * @param name 业务名，同时作为 {@link RetryBudget} 名称
     */
    public static <T> T call(String name, Callable<T> action) {
        return call(action, RetryPolicy.named(name));
    }

    public static <T> T call(Callable<T> action, RetryPolicy policy) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(policy, "policy");
        RetryBudget budget = policy.budget();
        long startNanos = System.nanoTime();
        long maxElapsedNanos = policy.maxElapsed().toNanos();
        Throwable last = null;
        for (int attempt = 1; attempt <= policy.maxAttempts(); attempt++) {
            throwIfInterrupted();
            if (attempt == 1) {
                budget.recordPrimary();
            } else {
                if (!budget.tryRetry()) {
                    log.warn("重试预算已用尽 budget={}，停止重试", budget.name());
                    break;
                }
                long delay = policy.backoffMillis(attempt - 1);
                long remainNanos = maxElapsedNanos - (System.nanoTime() - startNanos);
                if (remainNanos <= 0L) {
                    break;
                }
                long delayNanos = TimeUnit.MILLISECONDS.toNanos(delay);
                if (delayNanos >= remainNanos) {
                    break;
                }
                sleep(delay);
            }
            try {
                return action.call();
            } catch (Throwable error) {
                last = error;
                if (!policy.shouldRetry(error)) {
                    break;
                }
            }
        }
        throw asUnchecked(last);
    }

    /**
     * 使用默认策略重试。全进程共用名为 {@code default} 的预算，生产请改用 {@link #run(String, Runnable)}。
     */
    public static void run(Runnable action) {
        run(action, RetryPolicy.defaults());
    }

    /**
     * 使用按业务名隔离的默认策略重试。
     *
     * @param name 业务名，同时作为 {@link RetryBudget} 名称
     */
    public static void run(String name, Runnable action) {
        run(action, RetryPolicy.named(name));
    }

    public static void run(Runnable action, RetryPolicy policy) {
        Objects.requireNonNull(action, "action");
        call(() -> {
            action.run();
            return null;
        }, policy);
    }

    private static void throwIfInterrupted() {
        if (Thread.currentThread().isInterrupted()) {
            throw new UncheckedInterruptedException(new InterruptedException("重试前线程已中断"));
        }
    }

    private static void sleep(long delayMillis) {
        if (delayMillis <= 0L) {
            return;
        }
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UncheckedInterruptedException(e);
        }
    }

    private static RuntimeException asUnchecked(Throwable error) {
        return wrapUnchecked(error);
    }

    private static RuntimeException wrapUnchecked(Throwable error) {
        if (error == null) {
            return new IllegalStateException("重试失败但没有异常");
        }
        if (error instanceof Error fatal) {
            throw fatal;
        }
        if (error instanceof InterruptedException interrupted) {
            return new UncheckedInterruptedException(interrupted);
        }
        if (error instanceof java.util.concurrent.TimeoutException timeout) {
            return new UncheckedTimeoutException(timeout);
        }
        if (error instanceof RuntimeException runtime) {
            return runtime;
        }
        return new RuntimeException(error);
    }

    /**
     * 保留原因：TimeoutException / InterruptedException 被包成普通 RuntimeException，调用方接不住语义。
     */
    @SuppressWarnings("unused")
    private static RuntimeException asUncheckedForRollback(Throwable error) {
        if (error == null) {
            return new IllegalStateException("重试失败但没有异常");
        }
        if (error instanceof Error fatal) {
            throw fatal;
        }
        if (error instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
        if (error instanceof RuntimeException runtime) {
            return runtime;
        }
        return new RuntimeException(error);
    }
}
