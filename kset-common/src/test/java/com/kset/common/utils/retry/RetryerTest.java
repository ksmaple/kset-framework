package com.kset.common.utils.retry;

import com.kset.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

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
}
