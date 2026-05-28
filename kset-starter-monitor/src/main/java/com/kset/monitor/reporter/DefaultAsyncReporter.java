package com.kset.monitor.reporter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 有界队列异步上报，队列满时丢弃并记告警。
 */
public final class DefaultAsyncReporter implements AsyncReporter {

    private static final Logger log = LoggerFactory.getLogger(DefaultAsyncReporter.class);

    private final BlockingQueue<Runnable> queue;
    private final ExecutorService executor;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public DefaultAsyncReporter(int queueCapacity) {
        this.queue = new ArrayBlockingQueue<>(Math.max(64, queueCapacity));
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "kset-monitor-async-reporter");
            t.setDaemon(true);
            return t;
        });
        this.executor.submit(this::drainLoop);
    }

    @Override
    public void report(Runnable task) {
        if (!running.get() || task == null) {
            return;
        }
        if (!queue.offer(task)) {
            log.warn("monitor async reporter queue full, task dropped");
        }
    }

    private void drainLoop() {
        while (running.get()) {
            try {
                Runnable task = queue.poll(1, TimeUnit.SECONDS);
                if (task != null) {
                    task.run();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.debug("monitor async reporter task failed", e);
            }
        }
    }

    @Override
    public void shutdown() {
        running.set(false);
        executor.shutdownNow();
    }
}
