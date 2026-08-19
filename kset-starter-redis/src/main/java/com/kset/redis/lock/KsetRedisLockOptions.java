package com.kset.redis.lock;

import java.time.Duration;
import java.util.Objects;

/**
 * 抢锁参数。
 *
 * <p>Watchdog 只能通过 {@link #rejectNowWatchdog()}、{@link #waitThenFailWatchdog(Duration)}、
 * {@link #blockUntilWatchdog()} 或 {@code @KsetLocked(lease = "0s")} 打开。
 * {@link #rejectNow(Duration)} 传入 {@link Duration#ZERO} 会在执行时按 TTL 策略拒绝。
 */
public final class KsetRedisLockOptions {

    private final Duration waitTime;
    private final Duration leaseTime;
    private final KsetRedisLockStrategy strategy;
    private final boolean watchdog;

    private KsetRedisLockOptions(Duration waitTime, Duration leaseTime, KsetRedisLockStrategy strategy, boolean watchdog) {
        this.waitTime = waitTime;
        this.leaseTime = leaseTime;
        this.strategy = Objects.requireNonNull(strategy, "strategy");
        this.watchdog = watchdog;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 立即抢锁，固定租约。{@code leaseTime} 必须大于 0。
     */
    public static KsetRedisLockOptions rejectNow(Duration leaseTime) {
        return builder().waitTime(Duration.ZERO).leaseTime(leaseTime).strategy(KsetRedisLockStrategy.REJECT_IF_BUSY).build();
    }

    /**
     * Immediate reject, Redisson watchdog renews the lock until unlock.
     */
    public static KsetRedisLockOptions rejectNowWatchdog() {
        return builder()
                .waitTime(Duration.ZERO)
                .strategy(KsetRedisLockStrategy.REJECT_IF_BUSY)
                .watchdog(true)
                .build();
    }

    public static KsetRedisLockOptions waitThenFail(Duration waitTime, Duration leaseTime) {
        return builder().waitTime(waitTime).leaseTime(leaseTime).strategy(KsetRedisLockStrategy.WAIT_THEN_FAIL).build();
    }

    /**
     * Wait then fail, Redisson watchdog renews the lock until unlock.
     */
    public static KsetRedisLockOptions waitThenFailWatchdog(Duration waitTime) {
        return builder()
                .waitTime(waitTime)
                .strategy(KsetRedisLockStrategy.WAIT_THEN_FAIL)
                .watchdog(true)
                .build();
    }

    public static KsetRedisLockOptions blockUntil(Duration leaseTime) {
        return builder().strategy(KsetRedisLockStrategy.BLOCK_UNTIL_ACQUIRED).leaseTime(leaseTime).build();
    }

    /**
     * Block until acquired, Redisson watchdog renews the lock until unlock.
     */
    public static KsetRedisLockOptions blockUntilWatchdog() {
        return builder().strategy(KsetRedisLockStrategy.BLOCK_UNTIL_ACQUIRED).watchdog(true).build();
    }

    /**
     * Explicit watchdog APIs only. {@code leaseTime == 0} without {@link Builder#watchdog(boolean)} is rejected.
     */
    public boolean watchdog() {
        return watchdog;
    }

    /**
     * 保留原因：lease=0 被当成 watchdog，误传 Duration.ZERO 会拿到直到 unlock 才放的锁。
     */
    @SuppressWarnings("unused")
    private boolean watchdogForRollback() {
        return leaseTime != null && leaseTime.isZero();
    }

    public Duration waitTime() {
        return waitTime;
    }

    public Duration leaseTime() {
        return leaseTime;
    }

    public KsetRedisLockStrategy strategy() {
        return strategy;
    }

    public static final class Builder {

        private Duration waitTime;
        private Duration leaseTime;
        private KsetRedisLockStrategy strategy = KsetRedisLockStrategy.REJECT_IF_BUSY;
        private boolean watchdog;

        public Builder waitTime(Duration waitTime) {
            this.waitTime = waitTime;
            return this;
        }

        public Builder leaseTime(Duration leaseTime) {
            this.leaseTime = leaseTime;
            return this;
        }

        public Builder strategy(KsetRedisLockStrategy strategy) {
            this.strategy = strategy;
            return this;
        }

        public Builder watchdog(boolean watchdog) {
            this.watchdog = watchdog;
            return this;
        }

        public KsetRedisLockOptions build() {
            return new KsetRedisLockOptions(waitTime, leaseTime, strategy, watchdog);
        }
    }
}
