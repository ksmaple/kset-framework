package com.kset.redis.autoconfigure;

import com.kset.cloud.config.KsetRedisProperties;
import com.kset.redis.codec.KsetFastjsonRedisSerializer;
import com.kset.redis.config.KsetRedissonClientFactory;
import com.kset.redis.config.KsetRedisSerializerConfiguration;
import com.kset.redis.core.KsetRedisTtlPolicy;
import com.kset.redis.lock.KsetRedisLockExecutor;
import com.kset.redis.lock.internal.KsetRedissonLockProvider;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

@AutoConfiguration
@ConditionalOnClass(RedissonClient.class)
@ConditionalOnProperty(prefix = "kset.redis.redisson", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(KsetRedisProperties.class)
public class KsetRedissonAutoConfiguration {

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(RedissonClient.class)
    public RedissonClient ksetRedissonClient(RedisProperties springRedisProperties,
                                             KsetRedisProperties ksetRedisProperties,
                                             @Qualifier(KsetRedisSerializerConfiguration.BEAN_NAME)
                                             KsetFastjsonRedisSerializer valueSerializer) {
        return KsetRedissonClientFactory.createPrimary(springRedisProperties, ksetRedisProperties, valueSerializer);
    }

    @Bean
    @ConditionalOnMissingBean
    public KsetRedissonLockProvider ksetRedissonLockProvider(RedissonClient redissonClient,
                                                             KsetRedisProperties ksetRedisProperties,
                                                             KsetRedisTtlPolicy ttlPolicy) {
        return new KsetRedissonLockProvider(redissonClient, ksetRedisProperties, ttlPolicy);
    }

    @Bean
    @ConditionalOnMissingBean
    public KsetRedisLockExecutor ksetRedisLockExecutor(KsetRedissonLockProvider lockProvider,
                                                       KsetRedisTtlPolicy ttlPolicy,
                                                       Environment environment) {
        return new KsetRedisLockExecutor(lockProvider, ttlPolicy,
                KsetRedisServiceAutoConfiguration.monitorEnabled(environment));
    }
}
