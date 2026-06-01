package com.kset.cache.interceptor;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.StringJoiner;
import java.util.stream.Collectors;

/**
 * 默认 key 生成器，使用稳定的 simple 前缀格式。
 */
public class DefaultKsetCacheKeyGenerator implements KsetCacheKeyGenerator {

    @Override
    public String generate(Object target, Method method, Object[] args) {
        Object[] values = args != null ? args : new Object[0];
        if (values.length == 0) {
            return "simple:";
        }
        if (values.length == 1) {
            return "simple:" + formatValue(values[0]);
        }
        return java.util.Arrays.stream(values)
                .map(DefaultKsetCacheKeyGenerator::formatValue)
                .collect(Collectors.joining(",", "simple:[", "]"));
    }

    private static String formatValue(Object value) {
        if (value == null) {
            return "null";
        }
        Class<?> type = value.getClass();
        if (!type.isArray()) {
            return String.valueOf(value);
        }
        int length = Array.getLength(value);
        StringJoiner joiner = new StringJoiner(",", "[", "]");
        for (int i = 0; i < length; i++) {
            joiner.add(formatValue(Array.get(value, i)));
        }
        return joiner.toString();
    }
}
