package com.kset.redis.core;

import com.kset.redis.config.KsetRedisProperties;

import java.time.Duration;

/**
 * Redis 缓存 TTL 策略：禁止永久 key，未显式传入 TTL 时使用默认过期时间。
 */
public final class KsetRedisTtlPolicy {

    private final Duration defaultTtl;
    private final Duration maxTtl;

    public KsetRedisTtlPolicy(KsetRedisProperties properties) {
        this(properties.getDefaultTtl(), properties.getMaxTtl());
    }

    public KsetRedisTtlPolicy(Duration defaultTtl, Duration maxTtl) {
        this.defaultTtl = defaultTtl;
        this.maxTtl = maxTtl;
        if (defaultTtl == null || defaultTtl.isZero() || defaultTtl.isNegative()) {
            throw new IllegalStateException(
                    "kset.redis.default-ttl must be configured with a positive duration; permanent keys are forbidden");
        }
    }

    public Duration defaultTtl() {
        return defaultTtl;
    }

    /**
     * 解析有效 TTL：显式 ttl 优先，否则默认 ttl；并校验为正数、不超过 maxTtl。
     */
    public Duration requireTtl(Duration ttl) {
        Duration effective = ttl != null ? ttl : defaultTtl;
        return normalize(effective);
    }

    public Duration normalize(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException(
                    "Redis cache TTL is required and must be positive; permanent keys are forbidden");
        }
        if (maxTtl != null && ttl.compareTo(maxTtl) > 0) {
            return maxTtl;
        }
        return ttl;
    }
}
