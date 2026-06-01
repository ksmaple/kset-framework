package com.kset.cache.store;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.kset.cache.core.KsetCacheLayer;
import com.kset.cache.core.KsetCacheSpec;
import com.kset.cache.core.KsetCacheStore;
import com.kset.cache.core.KsetCacheValue;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

public class CaffeineKsetCacheStore implements KsetCacheStore {

    private final Cache<String, TimedValue> cache;

    public CaffeineKsetCacheStore(long maximumSize) {
        this.cache = Caffeine.newBuilder()
                .maximumSize(maximumSize > 0 ? maximumSize : 10_000)
                .build();
    }

    @Override
    public KsetCacheLayer layer() {
        return KsetCacheLayer.L1;
    }

    @Override
    public Optional<KsetCacheValue> get(KsetCacheSpec spec) {
        String key = spec.fullKey();
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
        cache.put(spec.fullKey(), new TimedValue(value, expiresAt(ttl)));
    }

    @Override
    public void evict(KsetCacheSpec spec) {
        cache.invalidate(spec.fullKey());
    }

    @Override
    public void clear(String cacheName) {
        String prefix = cacheName + "::";
        for (Map.Entry<String, TimedValue> entry : cache.asMap().entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                cache.invalidate(entry.getKey());
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
}
