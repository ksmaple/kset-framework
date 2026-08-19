package com.kset.common.utils.thread;

import com.kset.common.context.KsetContext;
import com.kset.common.context.KsetContextScope;
import com.kset.common.context.KsetContextSnapshot;
import com.kset.common.exception.UncheckedInterruptedException;
import com.kset.common.exception.UncheckedTimeoutException;
import com.kset.common.monitor.Monitor;
import com.kset.common.monitor.MonitorScope;
import com.kset.common.monitor.TraceSnapshot;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 一组独立任务并行执行，结果按原列表顺序返回。
 *
 * <p>默认走 {@link KsetThreadPoolFactory} 业务池，默认并发不超过 8，避免一次提交打满队列。
 * 失败时等已启动任务结束（或超时），第一条异常抛出，其余挂在 suppressed。
 * 工作线程会带上调用线程的 {@link com.kset.common.context.KsetContext} 与 Trace。
 * 用法见 {@code docs/usage/parallel.md}。
 */
public final class Parallel {

    private static final int DEFAULT_MAX_CONCURRENCY = 8;

    private Parallel() {
    }

    /**
     * 使用业务线程池并行映射，并发不超过 8。
     *
     * @param bizName 业务名，用于选择线程池
     */
    public static <T, R> List<R> map(String bizName, List<T> items, Function<T, R> fn) {
        return map(bizName, items, fn, defaultConcurrency(sizeOf(items)), null);
    }

    /**
     * 使用业务线程池并行映射。
     *
     * @param concurrency 同时在飞的任务数，至少为 1
     */
    public static <T, R> List<R> map(String bizName, List<T> items, Function<T, R> fn, int concurrency) {
        return map(bizName, items, fn, concurrency, null);
    }

    /**
     * 使用业务线程池并行映射，可限制总等待时间。
     *
     * @param timeout 整体超时，{@code null} 表示一直等到全部结束；超时后取消未完成任务
     */
    public static <T, R> List<R> map(String bizName, List<T> items, Function<T, R> fn,
                                     int concurrency, Duration timeout) {
        Objects.requireNonNull(bizName, "bizName");
        KsetThreadPoolFactory factory = KsetThreadPoolFactory.getInstance();
        return mapInternal(task -> factory.submit(bizName, task), items, fn, concurrency, timeout);
    }

    public static <T, R> List<R> map(Executor executor, List<T> items, Function<T, R> fn, int concurrency) {
        return map(executor, items, fn, concurrency, null);
    }

    public static <T, R> List<R> map(Executor executor, List<T> items, Function<T, R> fn,
                                     int concurrency, Duration timeout) {
        Objects.requireNonNull(executor, "executor");
        return mapInternal(task -> submitToExecutor(executor, task), items, fn, concurrency, timeout);
    }

    /**
     * 使用业务线程池并行执行，无返回值。
     */
    public static <T> void run(String bizName, List<T> items, Consumer<T> action) {
        run(bizName, items, action, defaultConcurrency(sizeOf(items)));
    }

    public static <T> void run(String bizName, List<T> items, Consumer<T> action, int concurrency) {
        Objects.requireNonNull(action, "action");
        map(bizName, items, item -> {
            action.accept(item);
            return null;
        }, concurrency, null);
    }

    static int defaultConcurrency(int size) {
        if (size <= 0) {
            return 1;
        }
        return Math.min(size, DEFAULT_MAX_CONCURRENCY);
    }

    private static int sizeOf(List<?> items) {
        return items == null ? 0 : items.size();
    }

    private static <T, R> List<R> mapInternal(TaskSubmitter submitter,
                                              List<T> items,
                                              Function<T, R> fn,
                                              int concurrency,
                                              Duration timeout) {
        Objects.requireNonNull(fn, "fn");
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        if (concurrency < 1) {
            throw new IllegalArgumentException("concurrency 至少为 1: " + concurrency);
        }
        if (timeout != null && timeout.isNegative()) {
            throw new IllegalArgumentException("timeout 不能为负");
        }
        int size = items.size();
        int limit = Math.min(concurrency, size);
        long deadlineNanos = timeout == null ? Long.MAX_VALUE : System.nanoTime() + timeout.toNanos();

        List<R> results = new ArrayList<>(size);
        Throwable[] errors = new Throwable[size];
        for (int i = 0; i < size; i++) {
            results.add(null);
        }

        Semaphore slots = new Semaphore(limit);
        CountDownLatch done = new CountDownLatch(size);
        List<Future<?>> futures = new ArrayList<>(size);
        boolean[] completed = new boolean[size];
        KsetContextSnapshot contextSnapshot = KsetContext.capture();
        TraceSnapshot traceSnapshot = Monitor.capture();

        for (int i = 0; i < size; i++) {
            long remain = remainNanos(deadlineNanos);
            try {
                if (remain <= 0L || !slots.tryAcquire(remain, TimeUnit.NANOSECONDS)) {
                    markTimeout(errors, i, size, done);
                    break;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                markTimeout(errors, i, size, done);
                errors[i] = e;
                break;
            }
            int index = i;
            T item = items.get(i);
            try {
                futures.add(submitter.submit(() -> {
                    try (KsetContextScope ignoredCtx = KsetContext.openScope(contextSnapshot);
                         MonitorScope ignoredTrace = Monitor.openScope(traceSnapshot)) {
                        R value = fn.apply(item);
                        if (!Thread.currentThread().isInterrupted()) {
                            results.set(index, value);
                        }
                    } catch (Throwable error) {
                        errors[index] = error;
                    } finally {
                        completed[index] = true;
                        slots.release();
                        done.countDown();
                    }
                }));
            } catch (RejectedExecutionException ex) {
                slots.release();
                errors[index] = ex;
                completed[index] = true;
                done.countDown();
            }
        }

        boolean timedOut = awaitDone(done, deadlineNanos, futures);
        if (timedOut) {
            fillUnfinishedTimeout(errors, completed);
        }
        throwIfFailed(errors);
        return results;
    }

    private static boolean awaitDone(CountDownLatch done, long deadlineNanos, List<Future<?>> futures) {
        try {
            return waitForDoneOrTimeout(done, deadlineNanos, futures);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            cancelAll(futures);
            throw new UncheckedInterruptedException(e);
        }
    }

    /**
     * 保留原因：remain<=0 不看 latch，已完成任务仍报超时，且丢掉已发生的业务异常。
     */
    @SuppressWarnings("unused")
    private static boolean awaitDoneForRollback(CountDownLatch done, long deadlineNanos, List<Future<?>> futures) {
        try {
            long remain = remainNanos(deadlineNanos);
            if (remain <= 0L || !done.await(remain, TimeUnit.NANOSECONDS)) {
                cancelAll(futures);
                return true;
            }
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            cancelAll(futures);
            throw new UncheckedInterruptedException(e);
        }
    }

    private static boolean waitForDoneOrTimeout(CountDownLatch done, long deadlineNanos, List<Future<?>> futures)
            throws InterruptedException {
        if (done.getCount() == 0L) {
            return false;
        }
        long remain = remainNanos(deadlineNanos);
        boolean finished = remain > 0L && done.await(remain, TimeUnit.NANOSECONDS);
        if (finished || done.getCount() == 0L) {
            return false;
        }
        cancelAll(futures);
        return true;
    }

    private static void fillUnfinishedTimeout(Throwable[] errors, boolean[] completed) {
        for (int i = 0; i < errors.length; i++) {
            if (errors[i] == null && !completed[i]) {
                errors[i] = new UncheckedTimeoutException("并行等待超时");
            }
        }
    }

    private static void markTimeout(Throwable[] errors, int from, int size, CountDownLatch done) {
        for (int j = from; j < size; j++) {
            if (errors[j] == null) {
                errors[j] = new UncheckedTimeoutException("并行等待超时");
            }
            done.countDown();
        }
    }

    private static void cancelAll(List<Future<?>> futures) {
        for (Future<?> future : futures) {
            if (future != null && !future.isDone()) {
                future.cancel(true);
            }
        }
    }

    private static void throwIfFailed(Throwable[] errors) {
        RuntimeException first = null;
        for (Throwable error : errors) {
            if (error == null) {
                continue;
            }
            RuntimeException wrapped = asUnchecked(error);
            if (first == null) {
                first = wrapped;
            } else {
                first.addSuppressed(error);
            }
        }
        if (first != null) {
            throw first;
        }
    }

    private static RuntimeException asUnchecked(Throwable error) {
        return wrapUnchecked(error);
    }

    private static RuntimeException wrapUnchecked(Throwable error) {
        if (error instanceof Error fatal) {
            throw fatal;
        }
        if (error instanceof InterruptedException interrupted) {
            return new UncheckedInterruptedException(interrupted);
        }
        if (error instanceof TimeoutException timeout) {
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
        if (error instanceof Error fatal) {
            throw fatal;
        }
        if (error instanceof RuntimeException runtime) {
            return runtime;
        }
        return new RuntimeException(error);
    }

    private static long remainNanos(long deadlineNanos) {
        if (deadlineNanos == Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return deadlineNanos - System.nanoTime();
    }

    private static Future<?> submitToExecutor(Executor executor, Runnable task) {
        if (executor instanceof ExecutorService service) {
            return service.submit(task);
        }
        CompletableFuture<Void> future = new CompletableFuture<>();
        executor.execute(() -> {
            try {
                task.run();
                future.complete(null);
            } catch (Throwable error) {
                future.completeExceptionally(error);
            }
        });
        return future;
    }

    @FunctionalInterface
    private interface TaskSubmitter {
        Future<?> submit(Runnable task);
    }
}
