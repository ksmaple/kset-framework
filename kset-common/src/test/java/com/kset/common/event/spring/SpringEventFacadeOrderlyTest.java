package com.kset.common.event.spring;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class SpringEventFacadeOrderlyTest {

    @Test
    void sameHashKeyIsSerialized() throws Exception {
        AtomicInteger current = new AtomicInteger();
        AtomicInteger max = new AtomicInteger();
        ApplicationEventPublisher publisher = event -> {
            int n = current.incrementAndGet();
            max.accumulateAndGet(n, Math::max);
            try {
                Thread.sleep(40);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                current.decrementAndGet();
            }
        };
        SpringEventFacade facade = new SpringEventFacade(publisher, null);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(4);
            for (int i = 0; i < 4; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        facade.publishOrderly("e", "user-1");
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(max.get()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
            facade.destroy();
        }
    }
}
