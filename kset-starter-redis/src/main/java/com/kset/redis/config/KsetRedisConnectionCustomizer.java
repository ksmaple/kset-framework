package com.kset.redis.config;

import com.kset.cloud.config.KsetRedisProperties;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

/**
 * Named Redis connection extension point.
 * <p>
 * Business code should customize framework-created Redis connections through this SPI instead of defining
 * {@link LettuceConnectionFactory} or RedisTemplate beans manually.
 */
public interface KsetRedisConnectionCustomizer {

    default void customizeStandalone(String sourceName,
                                     KsetRedisProperties.RedisSourceProperties source,
                                     RedisStandaloneConfiguration configuration) {
    }

    default void customizeCluster(String sourceName,
                                  KsetRedisProperties.RedisSourceProperties source,
                                  RedisClusterConfiguration configuration) {
    }

    default void customizePool(String sourceName,
                               KsetRedisProperties.RedisSourceProperties source,
                               KsetRedisProperties.RedisSourcePoolProperties pool) {
    }

    default void customizeClient(String sourceName,
                                 KsetRedisProperties.RedisSourceProperties source,
                                 LettuceClientConfiguration.LettuceClientConfigurationBuilder builder) {
    }

    default void customizeFactory(String sourceName,
                                  KsetRedisProperties.RedisSourceProperties source,
                                  LettuceConnectionFactory factory) {
    }
}
