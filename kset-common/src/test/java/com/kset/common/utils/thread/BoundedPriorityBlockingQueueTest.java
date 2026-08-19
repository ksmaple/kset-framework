package com.kset.common.utils.thread;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BoundedPriorityBlockingQueueTest {

    @Test
    void rejectsWhenFull() {
        BoundedPriorityBlockingQueue<Integer> queue = new BoundedPriorityBlockingQueue<>(2);
        assertThat(queue.offer(2)).isTrue();
        assertThat(queue.offer(1)).isTrue();
        assertThat(queue.offer(3)).isFalse();
        assertThatThrownBy(() -> queue.add(4)).isInstanceOf(IllegalStateException.class);
        assertThat(queue.poll()).isEqualTo(1);
        assertThat(queue.poll()).isEqualTo(2);
    }

    @Test
    void concurrentOfferDoesNotExceedCapacity() throws InterruptedException {
        int capacity = 50;
        BoundedPriorityBlockingQueue<Integer> queue = new BoundedPriorityBlockingQueue<>(capacity);
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger accepted = new AtomicInteger();
        try {
            for (int t = 0; t < threads; t++) {
                int offset = t * 1000;
                pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    for (int i = 0; i < 200; i++) {
                        if (queue.offer(offset + i)) {
                            accepted.incrementAndGet();
                        }
                    }
                    return null;
                });
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        assertThat(queue.size()).isLessThanOrEqualTo(capacity);
        assertThat(accepted.get()).isEqualTo(queue.size());
        assertThat(accepted.get()).isEqualTo(capacity);
    }

    @Test
    void timedOfferWaitsUntilCapacityFreed() throws Exception {
        BoundedPriorityBlockingQueue<Integer> queue = new BoundedPriorityBlockingQueue<>(1);
        assertThat(queue.offer(1)).isTrue();
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            var future = pool.submit(() -> queue.offer(2, 1, TimeUnit.SECONDS));
            Thread.sleep(50);
            assertThat(queue.poll()).isEqualTo(1);
            assertThat(future.get(1, TimeUnit.SECONDS)).isTrue();
            assertThat(queue.poll()).isEqualTo(2);
        } finally {
            pool.shutdownNow();
        }
    }
}
