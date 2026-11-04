package com.kset.common.utils.retry;

import com.kset.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetryerTest {

    @Test
    void succeedsWithoutRetry() {
        AtomicInteger calls = new AtomicInteger();

        String result = Retryer.call(() -> {
            calls.incrementAndGet();
            return "ok";
        }, RetryPolicy.maxAttempts(3).noBudget().withoutJitter().fixed(Duration.ofMillis(1)));

        assertThat(result).isEqualTo("ok");
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void succeedsOnThirdAttempt() {
        AtomicInteger calls = new AtomicInteger();

        String result = Retryer.call(() -> {
            if (calls.incrementAndGet() < 3) {
                throw new IllegalStateException("transient");
            }
            return "ok";
        }, RetryPolicy.maxAttempts(3).noBudget().withoutJitter().fixed(Duration.ofMillis(1)));

        assertThat(result).isEqualTo("ok");
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void doesNotRetryBusinessException() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> Retryer.call(() -> {
            calls.incrementAndGet();
            throw new BusinessException("规则失败");
        }, RetryPolicy.maxAttempts(5).noBudget().withoutJitter().fixed(Duration.ofMillis(1))))
                .isInstanceOf(BusinessException.class)
                .hasMessage("规则失败");
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void doesNotRetryWrappedBusinessException() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> Retryer.call(() -> {
            calls.incrementAndGet();
            throw new RuntimeException(new BusinessException("规则失败"));
        }, RetryPolicy.maxAttempts(5).noBudget().withoutJitter().fixed(Duration.ofMillis(1))))
                .isInstanceOf(RuntimeException.class)
                .hasCauseInstanceOf(BusinessException.class);
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void stopsWhenRetryBudgetExhausted() {
        RetryBudget budget = RetryBudget.of("retry-test-" + UUID.randomUUID(), 0d, 0, Duration.ofMinutes(1));
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> Retryer.call(() -> {
            calls.incrementAndGet();
            throw new IllegalStateException("down");
        }, RetryPolicy.maxAttempts(5).withoutJitter().fixed(Duration.ofMillis(1)).budget(budget)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("down");
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void namedBudgetsAreIsolated() {
        RetryBudget busy = RetryBudget.of("retry-busy-" + UUID.randomUUID(), 0d, 0, Duration.ofMinutes(1));
        RetryBudget other = RetryBudget.of("retry-other-" + UUID.randomUUID(), 1d, 10, Duration.ofMinutes(1));
        AtomicInteger busyCalls = new AtomicInteger();
        AtomicInteger otherCalls = new AtomicInteger();

        assertThatThrownBy(() -> Retryer.call(() -> {
            busyCalls.incrementAndGet();
            throw new IllegalStateException("down");
        }, RetryPolicy.maxAttempts(5).withoutJitter().fixed(Duration.ofMillis(1)).budget(busy)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(busyCalls.get()).isEqualTo(1);

        String result = Retryer.call(() -> {
            if (otherCalls.incrementAndGet() < 3) {
                throw new IllegalStateException("transient");
            }
            return "ok";
        }, RetryPolicy.maxAttempts(5).withoutJitter().fixed(Duration.ofMillis(1)).budget(other));
        assertThat(result).isEqualTo("ok");
        assertThat(otherCalls.get()).isEqualTo(3);
    }

    @Test
    void namedBudgetRejectsConflictingSettings() {
        String name = "retry-conflict-" + UUID.randomUUID();
        RetryBudget.of(name, 0.2d, 10, Duration.ofSeconds(10));
        assertThatThrownBy(() -> RetryBudget.of(name, 0.05d, 2, Duration.ofSeconds(10)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(name);
        assertThat(RetryBudget.of(name, 0.2d, 10, Duration.ofSeconds(10)).name()).isEqualTo(name);
    }

    @Test
    void rolloverStartsWithFreshCounters() {
        AtomicLong now = new AtomicLong(1_000L);
        RetryBudget budget = new RetryBudget("clocked", 1.0d, 0, 100L, now::get);

        budget.recordPrimary();
        assertThat(budget.tryRetry()).isTrue();
        assertThat(budget.tryRetry()).isFalse();

        now.addAndGet(100L);
        assertThat(budget.tryRetry()).isFalse();
        budget.recordPrimary();
        assertThat(budget.tryRetry()).isTrue();
        assertThat(budget.tryRetry()).isFalse();
    }

    @Test
    void concurrentRetriesDoNotExceedWindowAllowance() throws Exception {
        RetryBudget budget = new RetryBudget("concurrent", 1.0d, 0,
                10_000L, () -> 1_000L);
        for (int index = 0; index < 10; index++) {
            budget.recordPrimary();
        }
        int threads = 32;
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger accepted = new AtomicInteger();
        var pool = Executors.newFixedThreadPool(threads);
        try {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int index = 0; index < threads; index++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    if (budget.tryRetry()) {
                        accepted.incrementAndGet();
                    }
                    return null;
                }));
            }
            start.countDown();
            for (var future : futures) {
                future.get(2, TimeUnit.SECONDS);
            }
            assertThat(accepted.get()).isEqualTo(10);
        } finally {
            pool.shutdownNow();
        }
    }
}
