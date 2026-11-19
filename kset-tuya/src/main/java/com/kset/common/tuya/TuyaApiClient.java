package com.kset.common.tuya;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.kset.common.tuya.config.TuyaConfig;
import com.kset.common.tuya.model.TuyaResponse;
import com.kset.common.tuya.model.TuyaToken;
import com.kset.common.tuya.util.TuyaSignUtil;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 涂鸦智能API核心客户端
 * <p>
 * 功能：
 * <ul>
 *   <li>OAuth 2.0 简单模式获取Token（grant_type=1）</li>
 *   <li>refresh_token自动续期</li>
 *   <li>Token过期自动重试（业务请求返回token错误时）</li>
 *   <li>HMAC-SHA256请求签名（参考 RequestSignUtils 实现）</li>
 * </ul>
 * <p>
 * 签名算法：
 * <pre>
 * 令牌API: sign = HMAC-SHA256(clientId + t + nonce + stringToSign, secret)
 * 业务API: sign = HMAC-SHA256(clientId + accessToken + t + nonce + stringToSign, secret)
 * stringToSign = HTTPMethod + "\n" + SHA256(body) + "\n" + signedHeaders + "\n" + sortedURL
 * </pre>
 *
 * @author kset
 */
@Slf4j
public class TuyaApiClient {

    private static final String TOKEN_EXPIRED_CODE = "1010";

    private final TuyaConfig config;
    private final HttpClient httpClient;
    private volatile TuyaToken token;

    public TuyaApiClient(TuyaConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    // ==================== Token管理 ====================

    /**
     * 获取访问令牌（OAuth 2.0 简单模式，grant_type=1）
     */
    public synchronized void obtainToken() {
        log.info("获取涂鸦Token...");
        String path = "/v1.0/token?grant_type=1";
        TuyaResponse<TuyaToken> result = executeTokenRequest(path);
        handleTokenResponse(result, "获取");
    }

    /**
     * 使用refresh_token续期令牌
     */
    public synchronized void refreshTokenByRefreshToken() {
        if (token == null || token.getRefreshToken() == null) {
            log.warn("无可用refresh_token，重新获取Token");
            obtainToken();
            return;
        }

        log.info("使用refresh_token续期...");
        String path = "/v1.0/token/" + token.getRefreshToken();
        try {
            TuyaResponse<TuyaToken> result = executeTokenRequest(path);
            handleTokenResponse(result, "续期");
        } catch (Exception e) {
            log.warn("refresh_token续期失败，重新获取Token: {}", e.getMessage());
            obtainToken();
        }
    }

    /**
     * 获取有效的access_token
     * <p>
     * 策略：Token为空时获取新Token，过期时用refresh_token续期
     */
    public String getAccessToken() {
        if (token == null) {
            obtainToken();
        } else if (token.isExpired()) {
            refreshTokenByRefreshToken();
        }
        return token.getAccessToken();
    }

    /**
     * 执行Token类请求（签名不含access_token）
     */
    private TuyaResponse<TuyaToken> executeTokenRequest(String path) {
        long t = System.currentTimeMillis();

        // 构建stringToSign（body为空，使用EMPTY_HASH，URL查询参数自动排序）
        String stringToSign = TuyaSignUtil.buildStringToSign("GET", "", path);
        String sign = TuyaSignUtil.signForToken(
                config.getClientId(), config.getSecret(), t, stringToSign);

        // 使用排序后的path构建URL
        String sortedPath = TuyaSignUtil.sortQueryParams(path);
        String url = config.getApiUrl() + sortedPath;

        log.info("涂鸦Token请求: [GET {}]", url);
        log.info("涂鸦Token请求头: client_id={}, t={}, sign={}", config.getClientId(), t, sign);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .header("client_id", config.getClientId())
                    .header("sign", sign)
                    .header("t", String.valueOf(t))
                    .header("sign_method", "HMAC-SHA256")
                    .header("lang", "zh")
                    .header("Signature-Headers", "")
                    .header("nonce", "")
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            log.info("涂鸦Token响应: {}", response.body());

            return JSON.parseObject(
                    response.body(), new TypeReference<TuyaResponse<TuyaToken>>() {});
        } catch (Exception e) {
            log.error("涂鸦Token请求异常: {}", path, e);
            throw new RuntimeException("涂鸦Token请求异常", e);
        }
    }

    private void handleTokenResponse(TuyaResponse<TuyaToken> result, String action) {
        if (result != null && Boolean.TRUE.equals(result.getSuccess())) {
            this.token = result.getResult();
            this.token.setObtainedAt(System.currentTimeMillis());
            log.info("涂鸦Token{}成功, 有效期: {}秒", action, token.getExpireTime());
        } else {
            String errMsg = result != null ? result.getMsg() : "响应为空";
            log.error("涂鸦Token{}失败: code={}, msg={}",
                    action, result != null ? result.getCode() : null, errMsg);
            throw new RuntimeException("涂鸦Token" + action + "失败: " + errMsg);
        }
    }

    // ==================== 业务请求 ====================

    public <T> TuyaResponse<T> get(String path, TypeReference<TuyaResponse<T>> typeRef) {
        return execute("GET", path, null, typeRef);
    }

    public <T> TuyaResponse<T> post(String path, Object body,
                                     TypeReference<TuyaResponse<T>> typeRef) {
        return execute("POST", path, body, typeRef);
    }

    public <T> TuyaResponse<T> put(String path, Object body,
                                    TypeReference<TuyaResponse<T>> typeRef) {
        return execute("PUT", path, body, typeRef);
    }

    public <T> TuyaResponse<T> delete(String path, Object body,
                                       TypeReference<TuyaResponse<T>> typeRef) {
        return execute("DELETE", path, body, typeRef);
    }

    /**
     * 执行业务HTTP请求
     * <p>
     * Token过期时自动刷新并重试一次
     */
    private <T> TuyaResponse<T> execute(String method, String path, Object body,
                                         TypeReference<TuyaResponse<T>> typeRef) {
        TuyaResponse<T> result = doExecute(method, path, body, typeRef);

        // Token过期错误码，刷新后重试一次
        if (result != null && TOKEN_EXPIRED_CODE.equals(result.getCode())) {
            log.info("Token已过期，刷新后重试: [{} {}]", method, path);
            refreshTokenByRefreshToken();
            result = doExecute(method, path, body, typeRef);
        }

        return result;
    }

    /**
     * 实际执行HTTP请求
     * <p>
     * 签名流程参考 RequestSignUtils.execute：
     * 1. 序列化body
     * 2. 构建stringToSign（body hash + 排序URL）
     * 3. 计算sign
     * 4. 使用排序后的path构建请求URL
     */
    private <T> TuyaResponse<T> doExecute(String method, String path, Object body,
                                           TypeReference<TuyaResponse<T>> typeRef) {
        String accessToken = getAccessToken();
        String bodyStr = body != null ? JSON.toJSONString(body) : "";

        long t = System.currentTimeMillis();

        // 构建stringToSign（body hash和URL排序由TuyaSignUtil内部处理）
        String stringToSign = TuyaSignUtil.buildStringToSign(method, bodyStr, path);
        String sign = TuyaSignUtil.signForBusiness(
                config.getClientId(), accessToken, config.getSecret(), t, stringToSign);

        // 使用排序后的path构建URL
        String sortedPath = TuyaSignUtil.sortQueryParams(path);
        String url = config.getApiUrl() + sortedPath;

        log.info("涂鸦API请求: [{} {}]", method, url);
        log.info("涂鸦API请求头: client_id={}, access_token={}, t={}, sign={}", config.getClientId(), accessToken, t, sign);
        if (!bodyStr.isEmpty()) {
            log.info("涂鸦API请求体: {}", bodyStr);
        }

        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("client_id", config.getClientId())
                    .header("access_token", accessToken)
                    .header("sign", sign)
                    .header("t", String.valueOf(t))
                    .header("sign_method", "HMAC-SHA256")
                    .header("lang", "zh")
                    .header("Signature-Headers", "")
                    .header("nonce", "")
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(10));

            switch (method) {
                case "GET" -> requestBuilder.GET();
                case "POST" -> requestBuilder.POST(
                        HttpRequest.BodyPublishers.ofString(bodyStr, StandardCharsets.UTF_8));
                case "PUT" -> requestBuilder.PUT(
                        HttpRequest.BodyPublishers.ofString(bodyStr, StandardCharsets.UTF_8));
                case "DELETE" -> {
                    if (body != null) {
                        requestBuilder.method("DELETE",
                                HttpRequest.BodyPublishers.ofString(bodyStr, StandardCharsets.UTF_8));
                    } else {
                        requestBuilder.DELETE();
                    }
                }
                default -> throw new IllegalArgumentException("不支持的HTTP方法: " + method);
            }

            HttpResponse<String> response = httpClient.send(
                    requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            log.info("涂鸦API响应 [{} {}]: {}", method, path, response.body());

            TuyaResponse<T> result = JSON.parseObject(response.body(), typeRef);

            if (result != null && !Boolean.TRUE.equals(result.getSuccess())) {
                log.warn("涂鸦API业务失败 [{} {}]: code={}, msg={}",
                        method, path, result.getCode(), result.getMsg());
            }

            return result;
        } catch (Exception e) {
            log.error("涂鸦API请求异常 [{} {}]", method, path, e);
            throw new RuntimeException("涂鸦API请求异常: " + e.getMessage(), e);
        }
    }

    public TuyaConfig getConfig() {
        return config;
    }
}
