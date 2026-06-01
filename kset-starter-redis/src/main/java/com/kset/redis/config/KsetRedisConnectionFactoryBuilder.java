package com.kset.redis.config;

import com.kset.cloud.config.KsetRedisProperties;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.api.StatefulConnection;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 根据 {@link KsetRedisProperties.RedisSourceProperties} 构建 Lettuce 连接工厂（单机/集群）。
 */
public final class KsetRedisConnectionFactoryBuilder {

    private KsetRedisConnectionFactoryBuilder() {
    }

    public static LettuceConnectionFactory build(KsetRedisProperties.RedisSourceProperties source) {
        return build("", source, Collections.emptyList());
    }

    public static LettuceConnectionFactory build(String sourceName,
                                                 KsetRedisProperties.RedisSourceProperties source,
                                                 Collection<KsetRedisConnectionCustomizer> customizers) {
        Collection<KsetRedisConnectionCustomizer> safeCustomizers =
                customizers != null ? customizers : Collections.emptyList();
        LettuceClientConfiguration clientConfig = lettuceClientConfiguration(sourceName, source, safeCustomizers);
        LettuceConnectionFactory factory;
        if (source.isClusterMode()) {
            factory = new LettuceConnectionFactory(clusterConfiguration(sourceName, source, safeCustomizers), clientConfig);
        } else {
            factory = new LettuceConnectionFactory(standaloneConfiguration(sourceName, source, safeCustomizers), clientConfig);
        }
        safeCustomizers.forEach(customizer -> customizer.customizeFactory(sourceName, source, factory));
        factory.afterPropertiesSet();
        return factory;
    }

    private static RedisStandaloneConfiguration standaloneConfiguration(
            String sourceName,
            KsetRedisProperties.RedisSourceProperties source,
            Collection<KsetRedisConnectionCustomizer> customizers) {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(source.getHost(), source.getPort());
        config.setDatabase(source.getDatabase());
        if (StringUtils.hasText(source.getPassword())) {
            config.setPassword(RedisPassword.of(source.getPassword()));
        }
        customizers.forEach(customizer -> customizer.customizeStandalone(sourceName, source, config));
        return config;
    }

    private static RedisClusterConfiguration clusterConfiguration(
            String sourceName,
            KsetRedisProperties.RedisSourceProperties source,
            Collection<KsetRedisConnectionCustomizer> customizers) {
        KsetRedisProperties.Cluster cluster = source.getCluster();
        List<String> nodes = cluster.getNodes().stream()
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
        RedisClusterConfiguration config = new RedisClusterConfiguration(nodes);
        config.setMaxRedirects(cluster.getMaxRedirects());
        if (StringUtils.hasText(source.getPassword())) {
            config.setPassword(RedisPassword.of(source.getPassword()));
        }
        customizers.forEach(customizer -> customizer.customizeCluster(sourceName, source, config));
        return config;
    }

    private static LettuceClientConfiguration lettuceClientConfiguration(
            String sourceName,
            KsetRedisProperties.RedisSourceProperties source,
            Collection<KsetRedisConnectionCustomizer> customizers) {
        Duration timeout = source.getTimeout() != null ? source.getTimeout() : Duration.ofSeconds(2);
        LettuceClientConfiguration.LettuceClientConfigurationBuilder builder = source.getPool().isEnabled()
                ? LettucePoolingClientConfiguration.builder()
                        .poolConfig(poolConfig(sourceName, source, customizers))
                        .commandTimeout(timeout)
                : LettuceClientConfiguration.builder()
                        .commandTimeout(timeout);
        if (source.isSsl()) {
            builder.useSsl();
        }
        SocketOptions socketOptions = SocketOptions.builder()
                .connectTimeout(timeout)
                .build();
        builder.clientOptions(ClientOptions.builder().socketOptions(socketOptions).build());
        customizers.forEach(customizer -> customizer.customizeClient(sourceName, source, builder));
        return builder.build();
    }

    private static GenericObjectPoolConfig<StatefulConnection<?, ?>> poolConfig(
            String sourceName,
            KsetRedisProperties.RedisSourceProperties source,
            Collection<KsetRedisConnectionCustomizer> customizers) {
        KsetRedisProperties.RedisSourcePoolProperties pool = source.getPool();
        customizers.forEach(customizer -> customizer.customizePool(sourceName, source, pool));
        GenericObjectPoolConfig<StatefulConnection<?, ?>> config = new GenericObjectPoolConfig<>();
        config.setMaxTotal(pool.getMaxActive());
        config.setMaxIdle(pool.getMaxIdle());
        config.setMinIdle(pool.getMinIdle());
        if (pool.getMaxWait() != null) {
            config.setMaxWait(pool.getMaxWait());
        }
        return config;
    }
}
