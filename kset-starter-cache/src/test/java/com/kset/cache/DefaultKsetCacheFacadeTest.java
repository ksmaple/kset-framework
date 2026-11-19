package com.kset.cache;

import com.kset.cache.config.KsetCacheProperties;
import com.kset.cache.core.DefaultKsetCacheFacade;
import com.kset.cache.core.KsetCache;
import com.kset.cache.core.KsetCacheLayer;
import com.kset.cache.core.KsetCacheMetrics;
import com.kset.cache.core.KsetCacheSpec;
import com.kset.cache.core.KsetCacheStore;
import com.kset.cache.core.KsetCacheValue;
import com.kset.cache.store.CaffeineKsetCacheStore;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

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
    void l2BackfillDoesNotOutliveRemainingL2Ttl() {
        KsetCacheStore l1 = mock(KsetCacheStore.class);
        KsetCacheStore l2 = mock(KsetCacheStore.class);
        KsetCacheSpec spec = KsetCacheSpec.builder("user", "1")
                .layers(KsetCacheLayer.L1, KsetCacheLayer.L2).build();
        KsetCacheValue value = KsetCacheValue.of("alice");
        when(l1.layer()).thenReturn(KsetCacheLayer.L1);
        when(l2.layer()).thenReturn(KsetCacheLayer.L2);
        when(l2.namespaceGeneration("user")).thenReturn(OptionalLong.empty());
        when(l1.get(spec)).thenReturn(Optional.empty());
        when(l2.get(spec)).thenReturn(Optional.of(value));
        when(l2.remainingTtl(spec)).thenReturn(Optional.of(Duration.ofSeconds(2)));
        DefaultKsetCacheFacade facade = new DefaultKsetCacheFacade(List.of(l1, l2),
                new KsetCacheProperties());

        assertThat(facade.get(spec)).contains(value);

        ArgumentCaptor<Duration> ttl = ArgumentCaptor.forClass(Duration.class);
        verify(l1).put(org.mockito.ArgumentMatchers.eq(spec), org.mockito.ArgumentMatchers.eq(value),
                ttl.capture());
        assertThat(ttl.getValue()).isLessThanOrEqualTo(Duration.ofSeconds(2));
    }

    @Test
    void remoteClearInvalidatesAnotherInstancesL1() {
        KsetCacheStore sharedL2 = new VersionedL2Store();
        KsetCacheProperties properties = new KsetCacheProperties();
        DefaultKsetCacheFacade first = new DefaultKsetCacheFacade(
                List.of(new CaffeineKsetCacheStore(100), sharedL2), properties);
        DefaultKsetCacheFacade second = new DefaultKsetCacheFacade(
                List.of(new CaffeineKsetCacheStore(100), sharedL2), properties);
        KsetCacheSpec spec = KsetCacheSpec.builder("user", "1")
                .layers(KsetCacheLayer.L1, KsetCacheLayer.L2).build();

        first.put(spec, "old");
        assertThat(second.get(spec).map(KsetCacheValue::unwrap)).contains("old");
        first.clear("user", List.of(KsetCacheLayer.L1, KsetCacheLayer.L2));
        assertThat(second.get(spec)).isEmpty();
        first.put(spec, "new");
        assertThat(second.get(spec).map(KsetCacheValue::unwrap)).contains("new");
    }

    @Test
    void clearBetweenLayerWritesDoesNotPopulateNewGeneration() {
        VersionedL2Store l2 = new VersionedL2Store();
        KsetCacheStore l1 = mock(KsetCacheStore.class);
        when(l1.layer()).thenReturn(KsetCacheLayer.L1);
        doAnswer(invocation -> {
            l2.clear("user");
            return null;
        }).when(l1).put(org.mockito.ArgumentMatchers.any(KsetCacheSpec.class),
                org.mockito.ArgumentMatchers.any(KsetCacheValue.class),
                org.mockito.ArgumentMatchers.any(Duration.class), org.mockito.ArgumentMatchers.eq(0L));
        DefaultKsetCacheFacade facade = new DefaultKsetCacheFacade(List.of(l1, l2),
                new KsetCacheProperties());
        KsetCacheSpec spec = KsetCacheSpec.builder("user", "1")
                .layers(KsetCacheLayer.L1, KsetCacheLayer.L2).build();

        facade.put(spec, "old");

        assertThat(l2.get(spec)).isEmpty();
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

    private static final class VersionedL2Store implements KsetCacheStore {
        private final AtomicLong generation = new AtomicLong();
        private final Map<Long, KsetCacheValue> values = new ConcurrentHashMap<>();

        @Override
        public KsetCacheLayer layer() {
            return KsetCacheLayer.L2;
        }

        @Override
        public OptionalLong namespaceGeneration(String cacheName) {
            return OptionalLong.of(generation.get());
        }

        @Override
        public Optional<KsetCacheValue> get(KsetCacheSpec spec) {
            return Optional.ofNullable(values.get(generation.get()));
        }

        @Override
        public Optional<Duration> remainingTtl(KsetCacheSpec spec) {
            return Optional.of(Duration.ofMinutes(5));
        }

        @Override
        public void put(KsetCacheSpec spec, KsetCacheValue value, Duration ttl) {
            put(spec, value, ttl, generation.get());
        }

        @Override
        public void put(KsetCacheSpec spec, KsetCacheValue value, Duration ttl, long generation) {
            values.put(generation, value);
        }

        @Override
        public void evict(KsetCacheSpec spec) {
            values.remove(generation.get());
        }

        @Override
        public void clear(String cacheName) {
            generation.incrementAndGet();
        }
    }
}
