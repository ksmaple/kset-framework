package com.kset.agent.core.action;

import com.kset.agent.core.execution.AgentExecutionContext;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Bounded wait shared by action handlers; the caller owns cancellation of the future. */
final class CancellableActionWait {
    private CancellableActionWait() { }

    static long deadlineNanos(Instant now, Instant deadline) {
        long remaining = Math.max(0L, Duration.between(now, deadline).toNanos());
        return System.nanoTime() + remaining;
    }

    static <T> T await(Future<T> future, AgentExecutionContext context, long deadlineNanos)
            throws InterruptedException, ExecutionException, TimeoutException {
        while (true) {
            if (context.isCancellationRequested()) {
                throw new CancellationException("action cancelled");
            }
            long remaining = deadlineNanos - System.nanoTime();
            if (remaining <= 0L) {
                throw new TimeoutException("action deadline reached");
            }
            try {
                T result = future.get(Math.min(TimeUnit.MILLISECONDS.toNanos(50L), remaining),
                        TimeUnit.NANOSECONDS);
                if (context.isCancellationRequested()) {
                    throw new CancellationException("action cancelled");
                }
                return result;
            } catch (TimeoutException ignored) {
                // Poll cancellation and the deadline again.
            }
        }
    }
}
