package com.kset.common.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;

/**
 * AES-GCM 对称加密工具，用于持久化敏感数据（项目环境变量 secret 等）。
 *
 * <p>密钥来源: kset.security.env-secret-key（Base64 编码，长度需为 32 字节解码后）
 * <p>输出格式: Base64( IV(12B) || CipherText || AuthTag(16B) )
 */
@Component
public class CryptoUtil {

    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH_BIT = 128;
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private final SecretKeySpec keySpec;
    private final SecureRandom secureRandom = new SecureRandom();

    public CryptoUtil(@Value("${kset.security.env-secret-key:}") String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            this.keySpec = null;
            return;
        }
        byte[] keyBytes = Base64Codec.decode(base64Key);
        if (keyBytes.length != 32) {
            throw new IllegalStateException(
                    "kset.security.env-secret-key 必须为 Base64 编码的 32 字节(256 位)密钥,当前长度: " + keyBytes.length);
        }
        this.keySpec = new SecretKeySpec(keyBytes, ALGORITHM);
    }

    public String encrypt(String plain) {
        if (plain == null) {
            return null;
        }
        if (keySpec == null) {
            throw new IllegalStateException("加密密钥未配置");
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH_BIT, iv));
            byte[] cipherBytes = cipher.doFinal(plain.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            ByteBuffer buf = ByteBuffer.allocate(iv.length + cipherBytes.length);
            buf.put(iv).put(cipherBytes);
            return Base64Codec.encode(buf.array());
        } catch (Exception e) {
            throw new IllegalStateException("加密失败", e);
        }
    }

    public String decrypt(String cipherBase64) {
        if (cipherBase64 == null || cipherBase64.isEmpty()) {
            return cipherBase64;
        }
        if (keySpec == null) {
            throw new IllegalStateException("加密密钥未配置");
        }
        try {
            byte[] all = Base64Codec.decode(cipherBase64);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] cipherBytes = new byte[all.length - GCM_IV_LENGTH];
            System.arraycopy(all, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(all, GCM_IV_LENGTH, cipherBytes, 0, cipherBytes.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH_BIT, iv));
            return new String(cipher.doFinal(cipherBytes), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("解密失败", e);
        }
    }
}
