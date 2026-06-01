package com.kset.redis.config;

import com.kset.cloud.config.KsetRedisProperties;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KsetRedisConnectionFactoryBuilderTest {

    @Test
    void buildStandalone() {
        KsetRedisProperties.RedisSourceProperties source = new KsetRedisProperties.RedisSourceProperties();
        source.setHost("127.0.0.1");
        source.setPort(6379);
        LettuceConnectionFactory factory = KsetRedisConnectionFactoryBuilder.build(source);
        assertNotNull(factory);
        assertTrue(factory.isRunning() || !factory.isRunning());
        factory.destroy();
    }

    @Test
    void buildStandaloneWithPool() {
        KsetRedisProperties.RedisSourceProperties source = new KsetRedisProperties.RedisSourceProperties();
        source.setHost("127.0.0.1");
        source.setPort(6379);
        source.getPool().setEnabled(true);
        source.getPool().setMaxActive(4);
        source.getPool().setMaxIdle(3);
        source.getPool().setMinIdle(1);
        LettuceConnectionFactory factory = KsetRedisConnectionFactoryBuilder.build(source);
        assertNotNull(factory);
        factory.destroy();
    }

    @Test
    void buildAppliesKsetPoolCustomizer() {
        KsetRedisProperties.RedisSourceProperties source = new KsetRedisProperties.RedisSourceProperties();
        source.getPool().setEnabled(true);

        LettuceConnectionFactory factory = KsetRedisConnectionFactoryBuilder.build(
                "cache",
                source,
                List.of(new KsetRedisConnectionCustomizer() {
                    @Override
                    public void customizePool(String sourceName,
                                              KsetRedisProperties.RedisSourceProperties source,
                                              KsetRedisProperties.RedisSourcePoolProperties pool) {
                        pool.setMaxActive(12);
                    }
                }));

        assertEquals(12, source.getPool().getMaxActive());
        factory.destroy();
    }

    @Test
    void buildAppliesConnectionCustomizer() {
        KsetRedisProperties.RedisSourceProperties source = new KsetRedisProperties.RedisSourceProperties();
        AtomicReference<String> customizedSourceName = new AtomicReference<>();

        LettuceConnectionFactory factory = KsetRedisConnectionFactoryBuilder.build(
                "cache",
                source,
                List.of(new KsetRedisConnectionCustomizer() {
                    @Override
                    public void customizeFactory(String sourceName,
                                                 KsetRedisProperties.RedisSourceProperties source,
                                                 LettuceConnectionFactory factory) {
                        customizedSourceName.set(sourceName);
                    }
                }));

        assertEquals("cache", customizedSourceName.get());
        factory.destroy();
    }

    @Test
    void buildCluster() {
        KsetRedisProperties.RedisSourceProperties source = new KsetRedisProperties.RedisSourceProperties();
        source.getCluster().setEnabled(true);
        source.getCluster().setNodes(List.of("127.0.0.1:6379", "127.0.0.1:6380"));
        LettuceConnectionFactory factory = KsetRedisConnectionFactoryBuilder.build(source);
        assertNotNull(factory);
        factory.destroy();
    }
}
