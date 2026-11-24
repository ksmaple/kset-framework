package com.kset.image.generation;

/**
 * 一张生成结果图片：url 与 base64 至少一个非空，取决于供应商返回形式。
 */
public record GeneratedImage(String url, String base64) {

    public GeneratedImage {
        if ((url == null || url.isBlank()) && (base64 == null || base64.isBlank())) {
            throw new IllegalArgumentException("url 与 base64 至少一个非空");
        }
    }
}
