package com.kset.common.lifecycle;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class KsetGracefulShutdownsTest {

    @Test
    void drainsInFlightTasksWithinTimeout() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicBoolean done = new AtomicBoolean();
        executor.execute(() -> {
            sleep(200);
            done.set(true);
        });

        boolean drained = KsetGracefulShutdowns.shutdownGracefully(executor, "test-drain", Duration.ofSeconds(5));

        assertThat(drained).isTrue();
        assertThat(done).isTrue();
        assertThat(executor.isTerminated()).isTrue();
    }

    @Test
    void forcesShutdownNowWhenTimeoutExceeded() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicBoolean interrupted = new AtomicBoolean();
        executor.execute(() -> {
            try {
                Thread.sleep(30_000);
            } catch (InterruptedException e) {
                interrupted.set(true);
                Thread.currentThread().interrupt();
            }
        });

        boolean drained = KsetGracefulShutdowns.shutdownGracefully(executor, "test-forced", Duration.ofMillis(300));

        assertThat(drained).isFalse();
        assertThat(interrupted).isTrue();
        assertThat(executor.isShutdown()).isTrue();
    }

    @Test
    void awaitTerminationReturnsTrueForAlreadyTerminatedExecutor() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.shutdown();
        assertThat(executor.awaitTermination(3, TimeUnit.SECONDS)).isTrue();

        boolean drained = KsetGracefulShutdowns.awaitTermination(
                executor, "test-terminated", System.nanoTime() + TimeUnit.SECONDS.toNanos(1));

        assertThat(drained).isTrue();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
