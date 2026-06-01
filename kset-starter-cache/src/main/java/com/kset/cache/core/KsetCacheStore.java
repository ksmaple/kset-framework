package com.kset.cache.core;

import java.time.Duration;
import java.util.Optional;

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

    /**
     * 向当前层写入缓存值。
     */
    void put(KsetCacheSpec spec, KsetCacheValue value, Duration ttl);

    /**
     * 从当前层删除缓存值。
     */
    void evict(KsetCacheSpec spec);

    /**
     * Clear all entries of a cache namespace when the backend supports it.
     */
    default void clear(String cacheName) {
        throw new UnsupportedOperationException(layer() + " cache store does not support clear");
    }
}
