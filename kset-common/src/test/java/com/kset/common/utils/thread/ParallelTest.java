package com.kset.common.utils.thread;

import com.kset.common.context.KsetContext;
import com.kset.common.context.KsetContextKeys;
import com.kset.common.exception.UncheckedTimeoutException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParallelTest {

    @Test
    void keepsOrderAndEmptyList() {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Integer> mapped = Parallel.map(pool, List.of(1, 2, 3), value -> value * 10, 2);
            assertThat(mapped).containsExactly(10, 20, 30);
            assertThat(Parallel.map(pool, List.of(), value -> value, 1)).isEmpty();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void limitsConcurrency() throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        AtomicInteger current = new AtomicInteger();
        AtomicInteger max = new AtomicInteger();
        try {
            Parallel.map(pool, List.of(1, 2, 3, 4, 5, 6), value -> {
                int running = current.incrementAndGet();
                max.accumulateAndGet(running, Math::max);
                try {
                    Thread.sleep(30);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                } finally {
                    current.decrementAndGet();
                }
                return value;
            }, 2);
            assertThat(max.get()).isLessThanOrEqualTo(2);
        } finally {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void timeoutCancelsUnfinished() {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            assertThatThrownBy(() -> Parallel.map(pool, List.of(1, 2), value -> {
                try {
                    Thread.sleep(400);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
                return value;
            }, 2, Duration.ofMillis(50)))
                    .isInstanceOf(UncheckedTimeoutException.class);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void aggregatesFailuresAndPropagatesContext() {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        KsetContext.put(KsetContextKeys.TRACE_ID, "trace-parallel");
        try {
            List<String> seen = new CopyOnWriteArrayList<>();
            Parallel.map(pool, List.of("a", "b"), value -> {
                seen.add(KsetContext.get(KsetContextKeys.TRACE_ID).orElse("missing"));
                return value;
            }, 2);
            assertThat(seen).containsOnly("trace-parallel");

            assertThatThrownBy(() -> Parallel.map(pool, List.of(1, 2, 3), value -> {
                if (value == 1) {
                    throw new IllegalStateException("first");
                }
                if (value == 2) {
                    throw new IllegalStateException("second");
                }
                return value;
            }, 3))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("first")
                    .satisfies(error -> assertThat(error.getSuppressed()).hasSize(1));
        } finally {
            KsetContext.clear();
            pool.shutdownNow();
        }
    }

    @Test
    void timeoutKeepsFirstBusinessError() {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            assertThatThrownBy(() -> Parallel.map(pool, List.of(1, 2), value -> {
                if (value == 1) {
                    throw new IllegalStateException("first");
                }
                try {
                    Thread.sleep(400);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
                return value;
            }, 2, Duration.ofMillis(50)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("first");
        } finally {
            pool.shutdownNow();
        }
    }
}
