package com.kset.redis.config;

import com.kset.redis.codec.KsetStringRedisSerializer;
import com.kset.redis.key.KsetRedisKeys;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * 统一创建带 key 前缀与字符串值序列化的 {@link RedisTemplate}。
 */
public final class KsetRedisTemplateFactory {

    private KsetRedisTemplateFactory() {
    }

    public static RedisTemplate<String, Object> create(RedisConnectionFactory connectionFactory, String keyPrefix) {
        return create(connectionFactory, keyPrefix, new KsetStringRedisSerializer());
    }

    public static RedisTemplate<String, Object> create(RedisConnectionFactory connectionFactory,
                                                       String keyPrefix,
                                                       RedisSerializer<Object> valueSerializer) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer keySerializer = prefixingKeySerializer(keyPrefix);

        template.setKeySerializer(keySerializer);
        template.setHashKeySerializer(keySerializer);
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);
        template.afterPropertiesSet();
        return template;
    }

    static StringRedisSerializer prefixingKeySerializer(String keyPrefix) {
        return new StringRedisSerializer() {
            @Override
            public byte[] serialize(String key) {
                if (key == null) {
                    return super.serialize(null);
                }
                if (keyPrefix == null || keyPrefix.isBlank()) {
                    return super.serialize(key);
                }
                return super.serialize(KsetRedisKeys.joinPrefix(keyPrefix, key));
            }
        };
    }
}
