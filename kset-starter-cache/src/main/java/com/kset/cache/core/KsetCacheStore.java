package com.kset.cache.core;

import java.time.Duration;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * 单层缓存存储 SPI，具体实现可接入 Caffeine、Redis 或其他后端。
 */
public interface KsetCacheStore {

    /**
     * 当前存储对应的缓存层级。
     */
    KsetCacheLayer layer();

    /**
     * 从当前层读取缓存值。
     */
    Optional<KsetCacheValue> get(KsetCacheSpec spec);

    /** Versioned L1 access; stores without this capability do not retain shared-cache entries. */
    default Optional<KsetCacheValue> get(KsetCacheSpec spec, long generation) {
        return Optional.empty();
    }

    /** Namespace generation when the backing store supports cross-instance clear. */
    default OptionalLong namespaceGeneration(String cacheName) {
        return OptionalLong.empty();
    }

    /** Remaining lifetime, when the backend can determine it. */
    default Optional<Duration> remainingTtl(KsetCacheSpec spec) {
        return Optional.empty();
    }

    /**
     * 向当前层写入缓存值。
     */
    void put(KsetCacheSpec spec, KsetCacheValue value, Duration ttl);

    /** Writes to a fixed namespace generation when the backing store supports it. */
    default void put(KsetCacheSpec spec, KsetCacheValue value, Duration ttl, long generation) {
        if (layer() == KsetCacheLayer.L2) {
            throw new UnsupportedOperationException("versioned L2 put is not implemented");
        }
    }

    /**
     * 从当前层删除缓存值。
     */
    void evict(KsetCacheSpec spec);

    default void evict(KsetCacheSpec spec, long generation) {
        evict(spec);
    }

    /**
     * Clear all entries of a cache namespace when the backend supports it.
     */
    default void clear(String cacheName) {
        throw new UnsupportedOperationException(layer() + " cache store does not support clear");
    }
}
