package com.kset.agent.core.id;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * Thread-safe 64-bit Agent run ID generator. Hosts must assign a unique datacenter/worker pair to
 * every concurrently active process.
 */
public final class SnowflakeAgentRunIdGenerator implements AgentRunIdGenerator {

    public static final long DEFAULT_EPOCH_MILLIS =
            Instant.parse("2024-01-01T00:00:00Z").toEpochMilli();
    public static final long MAX_DATACENTER_ID = 31L;
    public static final long MAX_WORKER_ID = 31L;

    private static final long SEQUENCE_MASK = 4095L;
    private static final long WORKER_SHIFT = 12L;
    private static final long DATACENTER_SHIFT = 17L;
    private static final long TIMESTAMP_SHIFT = 22L;
    private static final long MAX_ELAPSED_MILLIS = (1L << 41) - 1L;

    private final long datacenterId;
    private final long workerId;
    private final long epochMillis;
    private final Clock clock;
    private long lastTimestamp = -1L;
    private long sequence;

    public SnowflakeAgentRunIdGenerator(long datacenterId, long workerId) {
        this(datacenterId, workerId, DEFAULT_EPOCH_MILLIS, Clock.systemUTC());
    }

    public SnowflakeAgentRunIdGenerator(
            long datacenterId, long workerId, long epochMillis, Clock clock) {
        if (datacenterId < 0L || datacenterId > MAX_DATACENTER_ID) {
            throw new IllegalArgumentException("datacenterId must be between 0 and 31");
        }
        if (workerId < 0L || workerId > MAX_WORKER_ID) {
            throw new IllegalArgumentException("workerId must be between 0 and 31");
        }
        if (epochMillis < 0L) {
            throw new IllegalArgumentException("epochMillis must not be negative");
        }
        this.datacenterId = datacenterId;
        this.workerId = workerId;
        this.epochMillis = epochMillis;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public synchronized String nextAgentRunId() {
        long timestamp = clock.millis();
        if (timestamp < lastTimestamp) {
            throw new IllegalStateException(
                    "clock moved backwards by " + (lastTimestamp - timestamp) + "ms");
        }
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1L) & SEQUENCE_MASK;
            if (sequence == 0L) {
                timestamp = waitForNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }

        long elapsed = timestamp - epochMillis;
        if (elapsed < 0L || elapsed > MAX_ELAPSED_MILLIS) {
            throw new IllegalStateException("current time is outside the Snowflake epoch range");
        }
        lastTimestamp = timestamp;
        long value = (elapsed << TIMESTAMP_SHIFT)
                | (datacenterId << DATACENTER_SHIFT)
                | (workerId << WORKER_SHIFT)
                | sequence;
        return Long.toString(value);
    }

    private long waitForNextMillis(long previousTimestamp) {
        long timestamp = clock.millis();
        while (timestamp <= previousTimestamp) {
            if (Thread.currentThread().isInterrupted()) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while waiting for Snowflake clock");
            }
            Thread.onSpinWait();
            timestamp = clock.millis();
        }
        return timestamp;
    }

    public long datacenterId() {
        return datacenterId;
    }

    public long workerId() {
        return workerId;
    }

    public long epochMillis() {
        return epochMillis;
    }
}
