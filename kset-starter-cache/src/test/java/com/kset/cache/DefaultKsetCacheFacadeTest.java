package com.kset.cache;

import com.kset.cache.config.KsetCacheProperties;
import com.kset.cache.core.DefaultKsetCacheFacade;
import com.kset.cache.core.KsetCache;
import com.kset.cache.core.KsetCacheLayer;
import com.kset.cache.core.KsetCacheMetrics;
import com.kset.cache.core.KsetCacheSpec;
import com.kset.cache.store.CaffeineKsetCacheStore;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultKsetCacheFacadeTest {

    @Test
    void singleFlightLoadsSameKeyOnce() throws Exception {
        KsetCacheProperties properties = new KsetCacheProperties();
        DefaultKsetCacheFacade facade = new DefaultKsetCacheFacade(
                List.of(new CaffeineKsetCacheStore(100)),
                properties);
        KsetCacheSpec spec = new KsetCacheSpec("user", "1", List.of(KsetCacheLayer.L1),
                Duration.ofMinutes(5), Duration.ofMinutes(1), true, String.class);
        AtomicInteger loads = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            Callable<Object> task = () -> {
                start.await();
                return facade.getOrLoad(List.of(spec), () -> {
                    loads.incrementAndGet();
                    Thread.sleep(50);
                    return "alice";
                });
            };
            List<Future<Object>> futures = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                futures.add(executor.submit(task));
            }
            start.countDown();
            for (Future<Object> future : futures) {
                assertThat(future.get()).isEqualTo("alice");
            }
            assertThat(loads).hasValue(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void programmingApiReturnsTypedValuesAndMetrics() throws Exception {
        KsetCacheProperties properties = new KsetCacheProperties();
        DefaultKsetCacheFacade facade = new DefaultKsetCacheFacade(
                List.of(new CaffeineKsetCacheStore(100)),
                properties);
        KsetCacheSpec spec = KsetCacheSpec.builder("user", "api:1")
                .layers(KsetCacheLayer.L1)
                .ttl(Duration.ofMinutes(5))
                .valueType(String.class)
                .build();

        assertThat(facade.getValue(spec, String.class)).isEmpty();
        String loaded = facade.getOrLoadValue(spec, String.class, () -> "alice");
        assertThat(loaded).isEqualTo("alice");
        assertThat(facade.getValue(spec, String.class)).contains("alice");

        KsetCacheMetrics metrics = facade.metrics();
        assertThat(metrics.misses()).isEqualTo(2);
        assertThat(metrics.l1Hits()).isEqualTo(1);
        assertThat(metrics.loads()).isEqualTo(1);
        assertThat(metrics.puts()).isEqualTo(1);
    }

    @Test
    void staticFacadeDelegatesToBoundFacade() throws Exception {
        KsetCacheProperties properties = new KsetCacheProperties();
        DefaultKsetCacheFacade facade = new DefaultKsetCacheFacade(
                List.of(new CaffeineKsetCacheStore(100)),
                properties);
        KsetCache.bind(facade);

        String loaded = KsetCache.getOrLoad(
                KsetCacheSpec.builder("user", "static:1").layers(KsetCacheLayer.L1).build(),
                String.class,
                () -> "bob");

        assertThat(loaded).isEqualTo("bob");
        assertThat(KsetCache.get("user", "static:1", String.class)).contains("bob");
    }
}
