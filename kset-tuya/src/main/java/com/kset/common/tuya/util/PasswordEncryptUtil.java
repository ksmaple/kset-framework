package com.kset.common.tuya.util;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * 涂鸦门锁密码加解密工具
 * <p>
 * 解密 ticket_key 并加密临时密码。
 * <ul>
 *   <li>算法: AES/ECB/PKCS5Padding</li>
 *   <li>密钥: 字符串的UTF-8字节（32字符 = AES-256，16字符 = AES-128）</li>
 *   <li>输出: 大写hex字符串</li>
 * </ul>
 *
 * @author chufeng
 * @date 2023/7/19 09:31
 */
public class PasswordEncryptUtil {

    private static final String AES_TRANSFORMATION = "AES/ECB/PKCS5Padding";
    private static final String AES = "AES";

    /**
     * 加密门锁临时密码（完整流程）
     * <p>
     * 1. 用 accessSecret 解密 ticket_key → 得到原始密钥
     * 2. 用原始密钥加密密码明文 → 输出hex
     *
     * @param plainTextPassword 密码明文（Wi-Fi锁7位 / Zigbee蓝牙锁6位）
     * @param accessSecret      开发者 Access Secret（32字符）
     * @param ticketKey          接口返回的加密 ticket_key（hex字符串）
     * @return 加密后的密码（大写hex字符串）
     * @throws Exception 加解密异常
     */
    public static String encryptPassword(String plainTextPassword, String accessSecret, String ticketKey) throws Exception {
        String plainTextTicketKey = decrypt(ticketKey, accessSecret);
        return encrypt(plainTextPassword, plainTextTicketKey);
    }

    /**
     * AES加密，输出大写hex
     *
     * @param plainText 明文
     * @param secretKey 密钥字符串（UTF-8字节作为AES密钥）
     * @return 大写hex密文
     */
    static String encrypt(String plainText, String secretKey) throws Exception {
        SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), AES);
        Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec);
        byte[] encryptedBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().withUpperCase().formatHex(encryptedBytes);
    }

    /**
     * AES解密hex密文
     *
     * @param encryptedHex hex密文
     * @param secretKey    密钥字符串（UTF-8字节作为AES密钥）
     * @return 解密后的明文
     */
    static String decrypt(String encryptedHex, String secretKey) throws Exception {
        SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), AES);
        Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec);
        byte[] decryptedBytes = cipher.doFinal(HexFormat.of().parseHex(encryptedHex));
        return new String(decryptedBytes, StandardCharsets.UTF_8);
    }
}
