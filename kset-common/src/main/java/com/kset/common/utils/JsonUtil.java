package com.kset.common.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 最简单的 JSON 字符串转换：对象 <-> 普通 JSON 文本，不写类型元数据。
 */
public final class JsonUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .findAndRegisterModules()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private JsonUtil() {
    }

    public static String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("JSON 序列化失败: " + value.getClass().getName(), e);
        }
    }

    public static <T> T fromJson(String json, Class<T> type) {
        if (json == null || type == null) {
            return null;
        }
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("JSON 反序列化失败: " + type.getName(), e);
        }
    }

    public static <T> T fromJson(String json, TypeReference<T> type) {
        if (json == null || type == null) {
            return null;
        }
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("JSON 反序列化失败: " + type.getType().getTypeName(), e);
        }
    }

    public static <T> T convert(Object raw, Class<T> type) {
        if (raw == null || type == null) {
            return null;
        }
        if (type.isInstance(raw)) {
            return type.cast(raw);
        }
        if (raw instanceof String text) {
            return fromJson(text, type);
        }
        try {
            return MAPPER.convertValue(raw, type);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("JSON 转换失败: " + type.getName(), e);
        }
    }

    public static <T> T convert(Object raw, TypeReference<T> type) {
        if (raw == null || type == null) {
            return null;
        }
        if (raw instanceof String text) {
            return fromJson(text, type);
        }
        try {
            return MAPPER.convertValue(raw, type);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("JSON 转换失败: " + type.getType().getTypeName(), e);
        }
    }
}
