package com.kset.agent.core.model;

/**
 * 图片内容值对象：用于多模态模型调用
 *
 * @param data     图片二进制数据
 * @param mimeType 图片 MIME 类型（如 image/png、image/jpeg）
 * @param name     图片名称（仅用于日志与展示，可为空）
 */
public record ImageContent(byte[] data, String mimeType, String name) {

    public ImageContent(byte[] data, String mimeType) {
        this(data, mimeType, null);
    }
}
