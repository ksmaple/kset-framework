package com.kset.mq.lifecycle;

import org.apache.rocketmq.client.support.RocketMQListenerContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.SmartLifecycle;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class KsetMqEventLifecycleTest {

    @Test
    void stopStopsRunningContainersOnce() {
        FakeContainer container = new FakeContainer(true);
        KsetMqEventLifecycle lifecycle = new KsetMqEventLifecycle(providerOf(container));

        lifecycle.start();
        lifecycle.stop();
        lifecycle.stop();

        assertThat(container.stopCalls.get()).isEqualTo(1);
        assertThat(container.isRunning()).isFalse();
    }

    @Test
    void stopSkipsContainersNotRunning() {
        FakeContainer container = new FakeContainer(false);
        KsetMqEventLifecycle lifecycle = new KsetMqEventLifecycle(providerOf(container));

        lifecycle.start();
        lifecycle.stop();

        assertThat(container.stopCalls.get()).isZero();
    }

    private static final class FakeContainer implements RocketMQListenerContainer, SmartLifecycle {
        private final AtomicInteger stopCalls = new AtomicInteger();
        private boolean running;

        private FakeContainer(boolean running) {
            this.running = running;
        }

        @Override
        public void start() {
            running = true;
        }

        @Override
        public void stop() {
            stopCalls.incrementAndGet();
            running = false;
        }

        @Override
        public boolean isRunning() {
            return running;
        }

        @Override
        public void destroy() {
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
