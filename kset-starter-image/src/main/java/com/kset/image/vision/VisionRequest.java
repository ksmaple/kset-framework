package com.kset.image.vision;

import java.nio.file.Path;
import java.util.Objects;

/**
 * 图片理解请求：图片来源三选一（URL / base64 / 本地文件路径），prompt 为对图片的提取或理解指令。
 * responseSchema 为可选的 JSON 结构说明提示词，不执行 JSON Schema 校验；解析结果放入 VisionResult.structuredParams。
 */
public record VisionRequest(String imageUrl, String imageBase64, Path imagePath,
                            String prompt, String responseSchema, Integer maxTokens) {

    public VisionRequest {
        int sources = (imageUrl != null && !imageUrl.isBlank() ? 1 : 0)
                + (imageBase64 != null && !imageBase64.isBlank() ? 1 : 0)
                + (imagePath != null ? 1 : 0);
        if (sources != 1) {
            throw new IllegalArgumentException("imageUrl、imageBase64、imagePath 必须且只能提供一个");
        }
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("prompt 不能为空");
        }
        if (maxTokens != null && maxTokens <= 0) {
            throw new IllegalArgumentException("maxTokens 必须为正数");
        }
    }

    /** 通过可公网访问的图片 URL 发起理解。 */
    public static VisionRequest ofUrl(String imageUrl, String prompt) {
        return new VisionRequest(imageUrl, null, null, prompt, null, null);
    }

    /** 通过 base64 图片内容发起理解（不含 data URL 前缀）。 */
    public static VisionRequest ofBase64(String imageBase64, String prompt) {
        return new VisionRequest(null, imageBase64, null, prompt, null, null);
    }

    /** 通过本地图片文件发起理解。 */
    public static VisionRequest ofPath(Path imagePath, String prompt) {
        return new VisionRequest(null, null, imagePath, prompt, null, null);
    }

    /** 要求模型按给定 JSON 结构说明返回结构化参数。 */
    public VisionRequest withResponseSchema(String responseSchema) {
        return new VisionRequest(imageUrl, imageBase64, imagePath, prompt,
                Objects.requireNonNull(responseSchema, "responseSchema"), maxTokens);
    }

    /** 覆盖本次请求的最大输出 token 数。 */
    public VisionRequest withMaxTokens(int maxTokens) {
        return new VisionRequest(imageUrl, imageBase64, imagePath, prompt, responseSchema, maxTokens);
    }
}
