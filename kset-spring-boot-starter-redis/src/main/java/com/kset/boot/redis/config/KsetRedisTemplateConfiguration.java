package com.kset.boot.redis.config;

import com.kset.cloud.config.KsetRedisProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class KsetRedisTemplateConfiguration {

    @Bean(name = {"ksetRedisTemplate", "redisTemplate"})
    @Primary
    @ConditionalOnMissingBean(RedisTemplate.class)
    public RedisTemplate<String, Object> ksetRedisTemplate(RedisConnectionFactory connectionFactory,
                                                           KsetRedisProperties properties) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer();

        StringRedisSerializer keySerializer = new StringRedisSerializer() {
            @Override
            public byte[] serialize(String key) {
                String prefix = properties.getKeyPrefix();
                if (prefix != null && !prefix.isBlank() && key != null && !key.startsWith(prefix)) {
                    return super.serialize(prefix + key);
                }
                return super.serialize(key);
            }
        };

        template.setKeySerializer(keySerializer);
        template.setHashKeySerializer(keySerializer);
        template.setValueSerializer(serializer);
        template.setHashValueSerializer(serializer);
        template.afterPropertiesSet();
        return template;
    }
}
