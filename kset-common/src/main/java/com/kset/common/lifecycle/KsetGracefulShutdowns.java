package com.kset.common.lifecycle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 执行器优雅关闭工具：shutdown → awaitTermination 排空 → 超时 shutdownNow 兜底，中断时恢复中断标志。
 */
public final class KsetGracefulShutdowns {

    private static final Logger log = LoggerFactory.getLogger(KsetGracefulShutdowns.class);

    /**
     * 强关后的短暂清理等待：让被中断任务完成 finally 清理（上下文还原、锁释放等）。
     */
    private static final long FORCE_AWAIT_MILLIS = 2_000L;

    private KsetGracefulShutdowns() {
    }

    /**
     * 优雅关闭单个执行器：先 shutdown，在给定超时内等待在途任务完成，超时后 shutdownNow 兜底。
     */
    public static boolean shutdownGracefully(ExecutorService executor, String name, Duration timeout) {
        executor.shutdown();
        return awaitTermination(executor, name, System.nanoTime() + timeout.toNanos());
    }

    /**
     * 在截止时间前等待执行器终止；超时或已无法等待时 shutdownNow 兜底。
     *
     * @return true 表示在预算内排空完成
     */
    public static boolean awaitTermination(ExecutorService executor, String name, long deadlineNanos) {
        long startNanos = System.nanoTime();
        try {
            long remaining = deadlineNanos - System.nanoTime();
            if (remaining > 0 && executor.awaitTermination(remaining, TimeUnit.NANOSECONDS)) {
                log.info("[kset-lifecycle] {} drained in {} ms", name, (System.nanoTime() - startNanos) / 1_000_000L);
                return true;
            }
            if (executor.isTerminated()) {
                log.info("[kset-lifecycle] {} drained in {} ms", name, (System.nanoTime() - startNanos) / 1_000_000L);
                return true;
            }
            List<Runnable> dropped = executor.shutdownNow();
            log.warn("[kset-lifecycle] {} did not drain before deadline, forced shutdownNow, queuedTasksDropped={}",
                    name, dropped.size());
            awaitForceCleanup(executor, name);
            return false;
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
            log.warn("[kset-lifecycle] {} awaitTermination interrupted, forced shutdownNow", name);
            return false;
        }
    }

    /**
     * 强关后最多等待 {@value #FORCE_AWAIT_MILLIS}ms，让被中断任务完成清理；不阻塞排空总预算。
     */
    private static void awaitForceCleanup(ExecutorService executor, String name) {
        try {
            if (!executor.awaitTermination(FORCE_AWAIT_MILLIS, TimeUnit.MILLISECONDS)) {
                log.warn("[kset-lifecycle] {} still not terminated {} ms after forced shutdown", name, FORCE_AWAIT_MILLIS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
