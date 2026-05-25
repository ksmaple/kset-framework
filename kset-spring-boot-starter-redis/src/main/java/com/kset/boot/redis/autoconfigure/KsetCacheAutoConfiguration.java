package com.kset.boot.redis.autoconfigure;

import com.kset.cloud.config.KsetRedisProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

@AutoConfiguration
@ConditionalOnClass(RedisCacheManager.class)
@ConditionalOnProperty(prefix = "kset.redis.cache", name = "enabled", havingValue = "true")
@EnableCaching
public class KsetCacheAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RedisCacheManager ksetRedisCacheManager(RedisConnectionFactory connectionFactory,
                                                   KsetRedisProperties properties) {
        Duration ttl = properties.getCache().getDefaultTtl();
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()));
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .build();
    }
}
