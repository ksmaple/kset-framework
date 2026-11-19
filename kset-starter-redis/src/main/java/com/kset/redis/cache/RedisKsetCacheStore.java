package com.kset.redis.cache;

import com.kset.cache.core.KsetCacheLayer;
import com.kset.cache.core.KsetCacheSpec;
import com.kset.cache.core.KsetCacheStore;
import com.kset.cache.core.KsetCacheValue;
import com.kset.redis.core.KsetRedisService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.ThreadLocalRandom;

public class RedisKsetCacheStore implements KsetCacheStore {

    private static final Duration GENERATION_TTL = Duration.ofDays(36_500);

    private final KsetRedisService redisService;

    public RedisKsetCacheStore(KsetRedisService redisService) {
        this.redisService = redisService;
    }

    @Override
    public KsetCacheLayer layer() {
        return KsetCacheLayer.L2;
    }

    @Override
    public OptionalLong namespaceGeneration(String cacheName) {
        return OptionalLong.of(generation(cacheName));
    }

    @Override
    public Optional<KsetCacheValue> get(KsetCacheSpec spec) {
        long generation = generation(spec.cacheName());
        KsetCacheValue value = redisService.get(dataKey(spec, generation), KsetCacheValue.class);
        return generation == generation(spec.cacheName()) ? Optional.ofNullable(value) : Optional.empty();
    }

    @Override
    public Optional<Duration> remainingTtl(KsetCacheSpec spec) {
        Long seconds = redisService.ttl(dataKey(spec, generation(spec.cacheName())));
        return seconds != null && seconds > 0
                ? Optional.of(Duration.ofSeconds(seconds)) : Optional.empty();
    }

    @Override
    public void put(KsetCacheSpec spec, KsetCacheValue value, Duration ttl) {
        put(spec, value, ttl, generation(spec.cacheName()));
    }

    @Override
    public void put(KsetCacheSpec spec, KsetCacheValue value, Duration ttl, long generation) {
        redisService.setEx(dataKey(spec, generation), value, ttl);
    }

    @Override
    public void evict(KsetCacheSpec spec) {
        redisService.delete(dataKey(spec, generation(spec.cacheName())));
    }

    @Override
    public void clear(String cacheName) {
        if (cacheName == null || cacheName.isBlank()) {
            throw new IllegalArgumentException("cacheName must not be blank");
        }
        long generation = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
        redisService.setEx(generationKey(cacheName), generation, GENERATION_TTL);
    }

    private long generation(String cacheName) {
        Long value = redisService.get(generationKey(cacheName), Long.class);
        return value != null ? value : 0L;
    }

    private String dataKey(KsetCacheSpec spec, long generation) {
        return spec.cacheName() + "::v" + generation + "::" + spec.key();
    }

    private String generationKey(String cacheName) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(cacheName.getBytes(StandardCharsets.UTF_8));
            return "kset:cache:generation:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }
}
