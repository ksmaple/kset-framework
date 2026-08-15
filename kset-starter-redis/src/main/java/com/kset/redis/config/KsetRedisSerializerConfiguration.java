package com.kset.redis.config;

import com.kset.redis.codec.KsetStringRedisSerializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class KsetRedisSerializerConfiguration {

    public static final String BEAN_NAME = "ksetRedisValueSerializer";

    @Bean(BEAN_NAME)
    @ConditionalOnMissingBean(name = BEAN_NAME)
    public KsetStringRedisSerializer ksetRedisValueSerializer() {
        return new KsetStringRedisSerializer();
    }
}
