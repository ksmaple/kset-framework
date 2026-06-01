package com.kset.redis.cache;

import com.kset.cache.core.KsetCacheLayer;
import com.kset.cache.core.KsetCacheSpec;
import com.kset.cache.core.KsetCacheStore;
import com.kset.cache.core.KsetCacheValue;
import com.kset.redis.core.KsetRedisService;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class RedisKsetCacheStore implements KsetCacheStore {

    private final KsetRedisService redisService;

    public RedisKsetCacheStore(KsetRedisService redisService) {
        this.redisService = redisService;
    }

    @Override
    public KsetCacheLayer layer() {
        return KsetCacheLayer.L2;
    }

    @Override
    public Optional<KsetCacheValue> get(KsetCacheSpec spec) {
        return Optional.ofNullable(redisService.get(spec.fullKey(), KsetCacheValue.class));
    }

    @Override
    public void put(KsetCacheSpec spec, KsetCacheValue value, Duration ttl) {
        redisService.setEx(spec.fullKey(), value, ttl);
    }

    @Override
    public void evict(KsetCacheSpec spec) {
        redisService.delete(spec.fullKey());
    }

    @Override
    public void clear(String cacheName) {
        List<String> keys = new ArrayList<>();
        redisService.scanKeys(cacheName + "::*", key -> {
            keys.add(key);
            if (keys.size() >= 500) {
                redisService.deleteAll(List.copyOf(keys));
                keys.clear();
            }
        });
        if (!keys.isEmpty()) {
            redisService.deleteAll(keys);
        }
    }
}
