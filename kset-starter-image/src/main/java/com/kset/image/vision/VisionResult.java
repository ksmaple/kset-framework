package com.kset.image.vision;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 图片理解结果：rawText 为模型原始回答；请求提供 responseSchema 且模型返回可解析 JSON 时，
 * structuredParams 为解析出的结构化参数，否则为空 Map。
 */
public record VisionResult(String rawText, Map<String, Object> structuredParams) {

    public VisionResult {
        if (rawText == null) {
            throw new IllegalArgumentException("rawText 不能为 null");
        }
        structuredParams = structuredParams == null ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(structuredParams));
    }
}
