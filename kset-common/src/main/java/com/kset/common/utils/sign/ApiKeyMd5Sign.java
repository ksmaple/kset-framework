package com.kset.common.utils.sign;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.TreeMap;

/**
 * 网关 API 签名：参数按键名排序，拼接为 {@code key=value&key=secret}，再做 MD5 并转大写。
 *
 * <p>与 {@link KsetSignUtil#signMd5} 的首尾 secret 包裹、小写摘要不是同一套规则。
 */
public final class ApiKeyMd5Sign {

    public static final long DEFAULT_TIMEOUT_MILLIS = 300_000L;

    private static final Logger log = LoggerFactory.getLogger(ApiKeyMd5Sign.class);

    private ApiKeyMd5Sign() {
    }

    /**
     * @return 签名；参数或密钥为空时返回 null
     */
    public static String sign(Map<String, String> params, String secretKey) {
        if (params == null || params.isEmpty()) {
            log.warn("生成签名失败: 参数为空");
            return null;
        }
        if (secretKey == null || secretKey.isEmpty()) {
            log.warn("生成签名失败: 密钥为空");
            return null;
        }
        TreeMap<String, String> sorted = new TreeMap<>(params);
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            if (builder.length() > 0) {
                builder.append('&');
            }
            builder.append(entry.getKey()).append('=').append(entry.getValue());
        }
        builder.append("&key=").append(secretKey);
        String sign = md5Upper(builder.toString());
        log.debug("生成签名: params={}, sign={}", sorted, sign);
        return sign;
    }

    public static boolean verify(Map<String, String> params, String sign, String timestamp,
                                 String secretKey, Long timeoutMillis) {
        if (params == null || sign == null || timestamp == null) {
            log.warn("签名验证失败: 参数为空");
            return false;
        }
        if (secretKey == null || secretKey.isEmpty()) {
            log.warn("签名验证失败: 密钥为空");
            return false;
        }
        if (!timestampValid(timestamp, timeoutMillis)) {
            return false;
        }
        return matches(params, sign, secretKey);
    }

    public static boolean verifyWithoutTimestamp(Map<String, String> params, String sign, String secretKey) {
        if (params == null || sign == null) {
            log.warn("签名验证失败: 参数为空");
            return false;
        }
        return matches(params, sign, secretKey);
    }

    public static boolean timestampValid(String timestamp, Long timeoutMillis) {
        if (timestamp == null || timestamp.isEmpty()) {
            return false;
        }
        long timeout = timeoutMillis == null ? DEFAULT_TIMEOUT_MILLIS : timeoutMillis;
        try {
            long requestTime = Long.parseLong(timestamp);
            long currentTime = System.currentTimeMillis();
            if (Math.abs(currentTime - requestTime) > timeout) {
                log.warn("签名验证失败: 时间戳超时, requestTime={}, currentTime={}, timeout={}",
                        requestTime, currentTime, timeout);
                return false;
            }
            return true;
        } catch (NumberFormatException error) {
            log.warn("签名验证失败: 时间戳格式错误, timestamp={}", timestamp);
            return false;
        }
    }

    private static boolean matches(Map<String, String> params, String sign, String secretKey) {
        String expected = sign(params, secretKey);
        boolean valid = expected != null && expected.equals(sign);
        if (!valid) {
            log.warn("签名验证失败: 签名不匹配, expected={}, actual={}", expected, sign);
        }
        return valid;
    }

    private static String md5Upper(String plain) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5")
                    .digest(plain.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(String.format("%02X", value));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("MD5 算法不可用", error);
        }
    }
}
