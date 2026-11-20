package com.kset.common.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;

/**
 * 带 {@code ENC:} 前缀的 AES/ECB/PKCS5Padding。
 *
 * <p>密钥是调用方传入的 UTF-8 字节，长度须为 16、24 或 32。用于已经按此格式入库的网关应用密钥。
 * 新的环境变量密钥继续使用 {@link CryptoUtil} 的 AES-GCM。
 */
public final class PrefixedAesEcb {

    public static final String PREFIX = "ENC:";

    private static final String TRANSFORMATION = "AES/ECB/PKCS5Padding";

    private PrefixedAesEcb() {
    }

    public static String encryptIfNeeded(String value, String cryptoKey) {
        if (!hasText(value) || value.startsWith(PREFIX)) {
            return value;
        }
        if (!hasText(cryptoKey)) {
            return value;
        }
        requireKeyLength(cryptoKey);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec(cryptoKey));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getEncoder().encodeToString(encrypted);
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("应用密钥加密失败", error);
        }
    }

    public static String decryptIfNeeded(String value, String cryptoKey) {
        if (!hasText(value) || !value.startsWith(PREFIX)) {
            return value;
        }
        if (!hasText(cryptoKey)) {
            throw new IllegalStateException("缺少应用密钥加密密钥，请配置 kset.gateway.secret.crypto-key");
        }
        requireKeyLength(cryptoKey);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec(cryptoKey));
            byte[] encrypted = Base64.getDecoder().decode(value.substring(PREFIX.length()));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException error) {
            throw new IllegalStateException("应用密钥解密失败", error);
        }
    }

    public static void requireKeyLength(String cryptoKey) {
        int keyLength = cryptoKey.getBytes(StandardCharsets.UTF_8).length;
        if (keyLength != 16 && keyLength != 24 && keyLength != 32) {
            throw new IllegalStateException("应用密钥加密密钥长度必须为 16/24/32 字节");
        }
    }

    private static SecretKeySpec keySpec(String cryptoKey) {
        return new SecretKeySpec(cryptoKey.getBytes(StandardCharsets.UTF_8), "AES");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
