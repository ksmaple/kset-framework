package com.kset.image.client;

import java.util.Base64;

/** 根据图片内容识别 MIME，避免将 PNG、GIF 或 WebP 统一标成 JPEG。 */
final class ImageDataUrls {

    private ImageDataUrls() {
    }

    static String fromBase64(String base64) {
        try {
            return fromBytes(Base64.getDecoder().decode(base64));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("图片须为有效的 PNG、JPEG、GIF 或 WebP base64", e);
        }
    }

    static String fromBytes(byte[] bytes) {
        return "data:" + mimeType(bytes) + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }

    private static String mimeType(byte[] bytes) {
        if (matches(bytes, 0, 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)) {
            return "image/png";
        }
        if (matches(bytes, 0, 0xff, 0xd8, 0xff)) {
            return "image/jpeg";
        }
        if (matches(bytes, 0, 'G', 'I', 'F', '8', '7', 'a')
                || matches(bytes, 0, 'G', 'I', 'F', '8', '9', 'a')) {
            return "image/gif";
        }
        if (matches(bytes, 0, 'R', 'I', 'F', 'F') && matches(bytes, 8, 'W', 'E', 'B', 'P')) {
            return "image/webp";
        }
        throw new IllegalArgumentException("仅支持 PNG、JPEG、GIF 或 WebP 图片内容");
    }

    private static boolean matches(byte[] bytes, int offset, int... signature) {
        if (bytes.length < offset + signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((bytes[offset + i] & 0xff) != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
