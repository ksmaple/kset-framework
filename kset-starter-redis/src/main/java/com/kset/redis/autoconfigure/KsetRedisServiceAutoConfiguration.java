package com.kset.redis.autoconfigure;

import com.kset.cloud.config.KsetRedisProperties;
import com.kset.redis.lock.KsetRedisLockExecutor;
import com.kset.redis.core.KsetRedisRegistry;
import com.kset.redis.core.KsetRedisService;
import com.kset.redis.core.KsetRedisStreamSettings;
import com.kset.redis.core.KsetRedisTtlPolicy;
import com.kset.redis.support.KsetRedisBootstrap;
import com.kset.redis.support.KsetRedisNamedSources;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.RedisTemplate;

@Configuration
public class KsetRedisServiceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public KsetRedisTtlPolicy ksetRedisTtlPolicy(KsetRedisProperties properties) {
        return new KsetRedisTtlPolicy(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public KsetRedisStreamSettings ksetRedisStreamSettings(KsetRedisProperties properties) {
        return new KsetRedisStreamSettings(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public KsetRedisRegistry ksetRedisRegistry() {
        return new KsetRedisRegistry();
    }

    @Bean(name = {"ksetRedisService"})
    @Primary
    @ConditionalOnBean(name = "ksetRedisTemplate")
    @ConditionalOnMissingBean(name = "ksetRedisService")
    public KsetRedisService ksetRedisService(@Qualifier("ksetRedisTemplate") RedisTemplate<String, Object> redisTemplate,
                                             KsetRedisTtlPolicy ttlPolicy,
                                             KsetRedisStreamSettings streamSettings,
                                             Environment environment) {
        if (monitorEnabled(environment)) {
            return KsetRedisService.monitoredFrom(KsetRedisRegistry.PRIMARY_NAME, redisTemplate, ttlPolicy, streamSettings);
        }
        return KsetRedisService.from(redisTemplate, ttlPolicy, streamSettings);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(KsetRedisService.class)
    public KsetRedisBootstrap ksetRedisBootstrap(KsetRedisRegistry registry,
                                                 KsetRedisService ksetRedisService,
                                                 ObjectProvider<KsetRedisNamedSources> namedSources,
                                                 ObjectProvider<KsetRedisLockExecutor> lockExecutor) {
        return new KsetRedisBootstrap(registry, ksetRedisService, namedSources, lockExecutor);
    }

    static boolean monitorEnabled(Environment environment) {
        return environment.getProperty("kset.monitor.enabled", Boolean.class, true)
                && environment.getProperty("kset.monitor.redis.enabled", Boolean.class, true);
    }
}
