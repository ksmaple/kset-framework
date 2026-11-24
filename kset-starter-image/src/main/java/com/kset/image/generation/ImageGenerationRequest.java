package com.kset.image.generation;

/**
 * 图片生成请求：prompt 为画面描述；size 为供应商尺寸规格（如 1024x1024）；count 为生成张数；
 * referenceImageUrl 为可选参考图，使用 OpenAI JSON 图片编辑协议。
 */
public record ImageGenerationRequest(String prompt, String size, Integer count, String referenceImageUrl) {

    public ImageGenerationRequest {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("prompt 不能为空");
        }
        if (count != null && count <= 0) {
            throw new IllegalArgumentException("count 必须为正数");
        }
        if (referenceImageUrl != null && referenceImageUrl.isBlank()) {
            throw new IllegalArgumentException("referenceImageUrl 不能为空白");
        }
    }

    /** 文生图。 */
    public static ImageGenerationRequest of(String prompt) {
        return new ImageGenerationRequest(prompt, null, null, null);
    }

    /** 图生图：以 referenceImageUrl 为参考图。 */
    public static ImageGenerationRequest ofReference(String referenceImageUrl, String prompt) {
        if (referenceImageUrl == null) {
            throw new IllegalArgumentException("referenceImageUrl 不能为 null");
        }
        return new ImageGenerationRequest(prompt, null, null, referenceImageUrl);
    }

    public ImageGenerationRequest withSize(String size) {
        return new ImageGenerationRequest(prompt, size, count, referenceImageUrl);
    }

    public ImageGenerationRequest withCount(int count) {
        return new ImageGenerationRequest(prompt, size, count, referenceImageUrl);
    }
}
