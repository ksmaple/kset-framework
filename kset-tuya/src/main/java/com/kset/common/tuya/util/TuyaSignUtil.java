package com.kset.common.tuya.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 涂鸦API签名工具类
 * <p>
 * 参考 RequestSignUtils 签名实现
 * <p>
 * 签名公式：
 * <ul>
 *   <li>Token请求: sign = HMAC-SHA256(clientId + t + nonce + stringToSign, secret)</li>
 *   <li>业务请求: sign = HMAC-SHA256(clientId + accessToken + t + nonce + stringToSign, secret)</li>
 * </ul>
 * stringToSign = HTTPMethod + "\n" + SHA256(body) + "\n" + signedHeaderLine + "\n" + sortedURL
 * <p>
 * URL中的查询参数会按字母序自动排序后参与签名。
 *
 * @author kset
 */
public class TuyaSignUtil {

    /** 空body的SHA256常量值 */
    public static final String EMPTY_HASH = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    /** Signature-Headers 请求头名称 */
    private static final String SIGN_HEADER_NAME = "Signature-Headers";

    private TuyaSignUtil() {
    }

    // ==================== 签名计算 ====================

    /**
     * 计算签名（Token请求，无access_token）
     * <p>
     * str = clientId + t + nonce + stringToSign
     *
     * @param clientId     开发者client_id
     * @param secret       开发者secret
     * @param t            13位时间戳
     * @param nonce        随机字符串（可为空）
     * @param stringToSign 待签名字符串
     * @return 签名（大写hex）
     */
    public static String signForToken(String clientId, String secret,
                                      long t, String nonce, String stringToSign) {
        StringBuilder sb = new StringBuilder();
        sb.append(clientId);
        sb.append(t);
        if (nonce != null && !nonce.isEmpty()) {
            sb.append(nonce);
        }
        sb.append(stringToSign);
        return hmacSha256(sb.toString(), secret);
    }

    /**
     * 计算签名（Token请求，无nonce）
     */
    public static String signForToken(String clientId, String secret,
                                      long t, String stringToSign) {
        return signForToken(clientId, secret, t, "", stringToSign);
    }

    /**
     * 计算签名（业务请求，包含access_token）
     * <p>
     * str = clientId + accessToken + t + nonce + stringToSign
     *
     * @param clientId     开发者client_id
     * @param accessToken  访问令牌
     * @param secret       开发者secret
     * @param t            13位时间戳
     * @param nonce        随机字符串（可为空）
     * @param stringToSign 待签名字符串
     * @return 签名（大写hex）
     */
    public static String signForBusiness(String clientId, String accessToken,
                                         String secret, long t,
                                         String nonce, String stringToSign) {
        StringBuilder sb = new StringBuilder();
        sb.append(clientId);
        if (accessToken != null && !accessToken.isEmpty()) {
            sb.append(accessToken);
        }
        sb.append(t);
        if (nonce != null && !nonce.isEmpty()) {
            sb.append(nonce);
        }
        sb.append(stringToSign);
        return hmacSha256(sb.toString(), secret);
    }

    /**
     * 计算签名（业务请求，无nonce）
     */
    public static String signForBusiness(String clientId, String accessToken,
                                         String secret, long t,
                                         String stringToSign) {
        return signForBusiness(clientId, accessToken, secret, t, "", stringToSign);
    }

    // ==================== stringToSign 构建 ====================

    /**
     * 构建stringToSign（自动处理body hash和URL排序）
     * <p>
     * 格式: METHOD + "\n" + SHA256(body) + "\n" + "\n" + sortedURL
     *
     * @param method HTTP方法
     * @param body   请求体（空或null使用EMPTY_HASH）
     * @param path   请求路径（含query参数，会自动排序）
     * @return stringToSign
     */
    public static String buildStringToSign(String method, String body, String path) {
        String bodyHash = (body == null || body.isEmpty()) ? EMPTY_HASH : sha256(body);
        String sortedPath = sortQueryParams(path);
        return method + "\n" + bodyHash + "\n" + "\n" + sortedPath;
    }

    /**
     * 构建stringToSign（Map方式，支持Signature-Headers、自动处理body hash和URL排序）
     * <p>
     * 格式: METHOD + "\n" + SHA256(body) + "\n" + signedHeaderLine + "\n" + sortedURL
     *
     * @param method  HTTP方法
     * @param body    请求体（空或null使用EMPTY_HASH）
     * @param headers 请求头map（用于提取Signature-Headers指定的header参与签名）
     * @param path    请求路径（含query参数，会自动排序）
     * @return stringToSign
     */
    public static String buildStringToSign(String method, String body,
                                           Map<String, String> headers, String path) {
        String bodyHash = (body == null || body.isEmpty()) ? EMPTY_HASH : sha256(body);
        String signHeaderNames = (headers != null) ? headers.getOrDefault(SIGN_HEADER_NAME, "") : "";
        String headerLine = buildSignedHeaderLine(headers, signHeaderNames);
        String sortedPath = sortQueryParams(path);
        return method + "\n" + bodyHash + "\n" + headerLine + "\n" + sortedPath;
    }

    /**
     * 构建stringToSign（字符串方式，兼容旧调用）
     * <p>
     * 格式: METHOD + "\n" + contentSha256 + "\n" + headers + "\n" + sortedURL
     *
     * @param method        HTTP方法 (GET/POST/PUT/DELETE)
     * @param contentSha256 请求体的SHA256值
     * @param headers       已构建的签名头行字符串（通常为空字符串）
     * @param url           请求路径（含query参数，会自动排序）
     * @return stringToSign
     */
    public static String buildStringToSign(String method, String contentSha256,
                                           String headers, String url) {
        String sortedUrl = sortQueryParams(url);
        return method + "\n" + contentSha256 + "\n" + headers + "\n" + sortedUrl;
    }

    // ==================== URL处理 ====================

    /**
     * 对URL中的查询参数按字母序排序
     * <p>
     * 参考 RequestSignUtils.getPathAndSortParam
     *
     * @param path 请求路径（如 /v1.0/token?b=2&a=1）
     * @return 排序后的路径（如 /v1.0/token?a=1&b=2）
     */
    public static String sortQueryParams(String path) {
        if (path == null || !path.contains("?")) {
            return path;
        }
        int idx = path.indexOf('?');
        String basePath = path.substring(0, idx);
        String query = path.substring(idx + 1);
        if (query.isEmpty()) {
            return basePath;
        }

        TreeMap<String, String> kvMap = new TreeMap<>();
        String[] kvs = query.split("&");
        for (String kv : kvs) {
            String[] kvArr = kv.split("=", 2);
            if (kvArr.length > 1) {
                kvMap.put(kvArr[0], kvArr[1]);
            } else {
                kvMap.put(kvArr[0], "");
            }
        }

        String sortedQuery = kvMap.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&"));
        return basePath + "?" + sortedQuery;
    }

    // ==================== 签名头行构建 ====================

    /**
     * 构建签名头行
     * <p>
     * 解析 Signature-Headers 指定的header名称，从headers map中获取对应值，
     * 格式: name1:value1\nname2:value2
     * <p>
     * 参考 RequestSignUtils.stringToSign 中的 headerLine 构建逻辑
     *
     * @param headers         请求头map
     * @param signHeaderNames Signature-Headers值（多个header名用:分隔）
     * @return 签名头行字符串
     */
    private static String buildSignedHeaderLine(Map<String, String> headers, String signHeaderNames) {
        if (signHeaderNames == null || signHeaderNames.isEmpty() || headers == null) {
            return "";
        }
        return Arrays.stream(signHeaderNames.split("\\s*:\\s*"))
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .map(name -> name + ":" + headers.getOrDefault(name, ""))
                .collect(Collectors.joining("\n"));
    }

    // ==================== 加密工具 ====================

    /**
     * 计算SHA256摘要
     *
     * @param content 内容
     * @return 小写hex编码的SHA256值
     */
    public static String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                    content == null ? new byte[0] : content.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256计算失败", e);
        }
    }

    /**
     * HMAC-SHA256签名并转大写
     *
     * @param message 待签名字符串
     * @param secret  密钥
     * @return 大写hex编码的HMAC-SHA256值
     */
    public static String hmacSha256(String message, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] hash = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash).toUpperCase();
        } catch (Exception e) {
            throw new RuntimeException("HMAC-SHA256签名计算失败", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
