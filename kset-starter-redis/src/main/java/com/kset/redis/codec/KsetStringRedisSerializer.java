package com.kset.redis.codec;

import com.kset.common.utils.JsonUtil;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

import java.nio.charset.StandardCharsets;

/**
 * Redis 值序列化器：只存普通字符串。基础类型用文本，复杂对象用 JSON 文本，不写类型元数据。
 */
public class KsetStringRedisSerializer implements RedisSerializer<Object> {

    @Override
    public byte[] serialize(Object value) throws SerializationException {
        if (value == null) {
            return new byte[0];
        }
        if (isBasicValue(value)) {
            return basicValueToString(value).getBytes(StandardCharsets.UTF_8);
        }
        return JsonUtil.toJson(value).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public Object deserialize(byte[] bytes) throws SerializationException {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static boolean isBasicType(Class<?> type) {
        return type.isPrimitive()
                || CharSequence.class.isAssignableFrom(type)
                || Number.class.isAssignableFrom(type)
                || Boolean.class == type
                || Character.class == type
                || Enum.class.isAssignableFrom(type);
    }

    private static boolean isBasicValue(Object value) {
        return isBasicType(value.getClass());
    }

    private static String basicValueToString(Object value) {
        if (value instanceof Enum<?> enumValue) {
            return enumValue.name();
        }
        return String.valueOf(value);
    }
}
