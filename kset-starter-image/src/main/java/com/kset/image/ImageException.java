package com.kset.image;

/**
 * 图片 AI 能力调用失败：网络错误、供应商拒绝或响应无法解析时抛出。
 */
public class ImageException extends RuntimeException {

    public ImageException(String message) {
        super(message);
    }

    public ImageException(String message, Throwable cause) {
        super(message, cause);
    }
}
