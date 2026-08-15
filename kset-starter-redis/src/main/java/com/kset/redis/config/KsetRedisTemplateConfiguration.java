package com.kset.redis.config;

import com.kset.cloud.config.KsetRedisProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

@Configuration
public class KsetRedisTemplateConfiguration {

    @Bean(name = "ksetRedisTemplate")
    @ConditionalOnMissingBean(name = "ksetRedisTemplate")
    public RedisTemplate<String, Object> ksetRedisTemplate(RedisConnectionFactory connectionFactory,
                                                           KsetRedisProperties properties,
                                                           @Qualifier(KsetRedisSerializerConfiguration.BEAN_NAME)
                                                           RedisSerializer<Object> valueSerializer) {
        return KsetRedisTemplateFactory.create(connectionFactory, properties.getKeyPrefix(), valueSerializer);
    }
}
