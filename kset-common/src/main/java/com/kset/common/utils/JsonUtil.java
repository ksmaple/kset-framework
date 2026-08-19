package com.kset.common.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 最简单的 JSON 字符串转换：对象 &lt;-&gt; 普通 JSON 文本，不写 {@code @type} / {@code @class}。
 *
 * <p>{@link #copy} 走 JSON 树，只适合小对象；大对象请用 MapStruct。
 * Web 对外日期格式由 {@code kset-starter-web} 的 ObjectMapper 负责，与本工具不一定相同。
 */
public final class JsonUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .findAndRegisterModules()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private JsonUtil() {
    }

    public static ObjectMapper mapper() {
        return MAPPER;
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

    public static JsonNode readTree(String json) {
        if (json == null) {
            return null;
        }
        try {
            return MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("JSON 解析失败", e);
        }
    }

    /**
     * 通过 JSON 树拷贝/转换对象，适合小对象。大对象请用 MapStruct，避免两次树转换的内存与 CPU 开销。
     */
    public static <T> T copy(Object source, Class<T> type) {
        if (source == null || type == null) {
            return null;
        }
        try {
            return MAPPER.convertValue(MAPPER.valueToTree(source), type);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("JSON 拷贝失败: " + source.getClass().getName(), e);
        }
    }

    /**
     * 通过 JSON 树拷贝/转换对象，适合小对象。大对象请用 MapStruct，避免两次树转换的内存与 CPU 开销。
     */
    public static <T> T copy(Object source, TypeReference<T> type) {
        if (source == null || type == null) {
            return null;
        }
        try {
            return MAPPER.convertValue(MAPPER.valueToTree(source), type);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("JSON 拷贝失败: " + source.getClass().getName(), e);
        }
    }
}
