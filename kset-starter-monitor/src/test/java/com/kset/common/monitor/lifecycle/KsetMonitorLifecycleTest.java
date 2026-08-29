package com.kset.common.monitor.lifecycle;

import com.kset.common.monitor.facade.MetricKind;
import com.kset.common.monitor.reporter.AsyncReporter;
import com.kset.common.monitor.reporter.MetricAggregator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class KsetMonitorLifecycleTest {

    @Test
    void stopFlushesMetricsAndShutsDownReportersOnce() {
        RecordingAggregator aggregator = new RecordingAggregator();
        RecordingReporter reporter = new RecordingReporter();
        KsetMonitorLifecycle lifecycle = new KsetMonitorLifecycle(
                providerOf(aggregator), providerOf(reporter));

        lifecycle.start();
        lifecycle.stop();
        lifecycle.stop();

        assertThat(aggregator.flushCalls.get()).isEqualTo(1);
        assertThat(reporter.shutdownCalls.get()).isEqualTo(1);
        assertThat(lifecycle.isRunning()).isFalse();
    }

    @Test
    void shutdownLifecycleWorksWithoutStart() {
        RecordingAggregator aggregator = new RecordingAggregator();
        KsetMonitorLifecycle lifecycle = new KsetMonitorLifecycle(
                providerOf(aggregator), providerOf());

        lifecycle.shutdownLifecycle();

        assertThat(aggregator.flushCalls.get()).isEqualTo(1);
    }

    private static final class RecordingAggregator implements MetricAggregator {
        private final AtomicInteger flushCalls = new AtomicInteger();

        @Override
        public void record(String name, long value, MetricKind kind) {
        }

        @Override
        public String flushSummary() {
            flushCalls.incrementAndGet();
            return "count[x]=1";
        }
    }

    private static final class RecordingReporter implements AsyncReporter {
        private final AtomicInteger shutdownCalls = new AtomicInteger();

        @Override
        public void report(Runnable task) {
        }

        @Override
        public void shutdown() {
            shutdownCalls.incrementAndGet();
        }
    }

    @SafeVarargs
    private static <T> ObjectProvider<T> providerOf(T... items) {
        List<T> list = List.of(items);
        return new ObjectProvider<>() {
            @Override
            public T getObject() {
                return list.get(0);
            }

            @Override
            public Iterator<T> iterator() {
                return list.iterator();
            }
        };
    }
}
