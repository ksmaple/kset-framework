package com.kset.auth.autoconfigure;

import com.kset.auth.config.KsetAuthProperties;
import com.kset.auth.session.LoginSessionStore;
import com.kset.auth.session.RedisLoginSessionStore;
import com.kset.redis.core.KsetRedisService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(afterName = "com.kset.redis.autoconfigure.KsetRedisAutoConfiguration")
@ConditionalOnClass(name = "com.kset.redis.core.KsetRedisService")
@ConditionalOnBean(type = "com.kset.redis.core.KsetRedisService")
@ConditionalOnProperty(prefix = "kset.auth", name = "enabled", havingValue = "true", matchIfMissing = true)
public class KsetAuthRedisSessionAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "kset.auth.session", name = "store-type", havingValue = "auto", matchIfMissing = true)
    public LoginSessionStore autoRedisLoginSessionStore(KsetRedisService redisService, KsetAuthProperties properties) {
        return new RedisLoginSessionStore(redisService, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "kset.auth.session", name = "store-type", havingValue = "redis")
    public LoginSessionStore redisLoginSessionStore(KsetRedisService redisService, KsetAuthProperties properties) {
        return new RedisLoginSessionStore(redisService, properties);
    }
}
