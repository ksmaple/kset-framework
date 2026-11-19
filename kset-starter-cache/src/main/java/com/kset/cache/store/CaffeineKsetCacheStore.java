package com.kset.cache.store;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.kset.cache.core.KsetCacheLayer;
import com.kset.cache.core.KsetCacheSpec;
import com.kset.cache.core.KsetCacheStore;
import com.kset.cache.core.KsetCacheValue;

import java.time.Duration;
import java.util.Optional;

public class CaffeineKsetCacheStore implements KsetCacheStore {

    private final Cache<CacheKey, TimedValue> cache;

    public CaffeineKsetCacheStore(long maximumSize) {
        this(maximumSize, 128, false);
    }

    public CaffeineKsetCacheStore(long maximumSize, int initialCapacity, boolean recordStats) {
        Caffeine<Object, Object> builder = Caffeine.newBuilder()
                .initialCapacity(initialCapacity > 0 ? initialCapacity : 128)
                .maximumSize(maximumSize > 0 ? maximumSize : 10_000);
        if (recordStats) {
            builder.recordStats();
        }
        this.cache = builder.build();
    }

    @Override
    public KsetCacheLayer layer() {
        return KsetCacheLayer.L1;
    }

    @Override
    public Optional<KsetCacheValue> get(KsetCacheSpec spec) {
        return read(spec, null);
    }

    @Override
    public Optional<KsetCacheValue> get(KsetCacheSpec spec, long generation) {
        return read(spec, generation);
    }

    private Optional<KsetCacheValue> read(KsetCacheSpec spec, Long generation) {
        CacheKey key = new CacheKey(spec.cacheName(), spec.key(), generation);
        TimedValue timed = cache.getIfPresent(key);
        if (timed == null) {
            return Optional.empty();
        }
        if (timed.expired()) {
            cache.invalidate(key);
            return Optional.empty();
        }
        return Optional.of(timed.value());
    }

    @Override
    public void put(KsetCacheSpec spec, KsetCacheValue value, Duration ttl) {
        write(spec, value, ttl, null);
    }

    @Override
    public void put(KsetCacheSpec spec, KsetCacheValue value, Duration ttl, long generation) {
        write(spec, value, ttl, generation);
    }

    private void write(KsetCacheSpec spec, KsetCacheValue value, Duration ttl, Long generation) {
        cache.put(new CacheKey(spec.cacheName(), spec.key(), generation),
                new TimedValue(value, expiresAt(ttl)));
    }

    @Override
    public void evict(KsetCacheSpec spec) {
        cache.invalidate(new CacheKey(spec.cacheName(), spec.key(), null));
    }

    @Override
    public void evict(KsetCacheSpec spec, long generation) {
        cache.invalidate(new CacheKey(spec.cacheName(), spec.key(), generation));
    }

    @Override
    public void clear(String cacheName) {
        for (CacheKey key : cache.asMap().keySet()) {
            if (key.cacheName().equals(cacheName)) {
                cache.invalidate(key);
            }
        }
    }

    private static long expiresAt(Duration ttl) {
        Duration effective = ttl != null && !ttl.isNegative() && !ttl.isZero() ? ttl : Duration.ofMinutes(5);
        return System.nanoTime() + effective.toNanos();
    }

    private record TimedValue(KsetCacheValue value, long expiresAtNanos) {
        boolean expired() {
            return System.nanoTime() >= expiresAtNanos;
        }
    }

    private record CacheKey(String cacheName, String key, Long generation) {
    }
}
