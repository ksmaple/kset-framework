package com.kset.common.cache;

import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

/**
 * 构造 Spring Redis 缓存。键前缀为 {@code prefix + cacheName + "::"}，值使用 JSON，不缓存 null。
 */
public final class RedisJsonCacheFactory {

    private RedisJsonCacheFactory() {
    }

    public static RedisCacheManager create(RedisConnectionFactory connectionFactory, Duration ttl, String keyPrefix) {
        if (connectionFactory == null) {
            throw new IllegalArgumentException("connectionFactory 不能为空");
        }
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl 必须为正数");
        }
        String prefix = keyPrefix == null ? "" : keyPrefix;
        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer();
        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .computePrefixWith(cacheName -> prefix + cacheName + "::")
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer));
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaults)
                .transactionAware()
                .build();
    }
}
