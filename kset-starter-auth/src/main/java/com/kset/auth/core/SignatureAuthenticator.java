package com.kset.auth.core;

import com.kset.auth.config.KsetAuthProperties;
import com.kset.auth.spi.Authenticator;
import com.kset.common.auth.LoginUser;
import com.kset.common.utils.sign.KsetSignUtil;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

public class SignatureAuthenticator implements Authenticator {

    private final KsetAuthProperties properties;

    public SignatureAuthenticator(KsetAuthProperties properties) {
        this.properties = properties;
    }

    @Override
    public String scheme() {
        return AuthSchemes.SIGNATURE;
    }

    @Override
    public AuthResult authenticate(AuthRequest request, AuthRuleMatch match) {
        KsetAuthProperties.AppKey appKeyProperties = properties.getAppKey();
        if (!appKeyProperties.isEnabled()) {
            return AuthResult.failure(401, "未登录");
        }
        String appKey = AppKeySupport.resolveAppKey(request, appKeyProperties);
        return appKeyProperties.findApp(appKey)
                .filter(app -> AppKeySupport.hasText(app.getSecret()))
                .filter(app -> verify(request, appKeyProperties, app))
                .map(app -> {
                    LoginUser user = AppKeySupport.buildUser(app, match.getSubject());
                    return AuthResult.authenticated(user);
                })
                .orElseGet(() -> AuthResult.failure(401, "未登录"));
    }

    private boolean verify(AuthRequest request,
                           KsetAuthProperties.AppKey appKeyProperties,
                           KsetAuthProperties.App app) {
        KsetSignUtil signer = KsetSignUtil.of(app.getSecret(), appKeyProperties.getSignField());
        String sign = resolveSign(request, appKeyProperties);
        if (request.getBodySha256() != null && validTimestamp(request, appKeyProperties, true)) {
            Map<String, String> signedHeaders = new LinkedHashMap<>();
            putHeader(signedHeaders, request, appKeyProperties.getAppKeyHeader());
            putHeader(signedHeaders, request, appKeyProperties.getTimestampHeader());
            putHeader(signedHeaders, request, appKeyProperties.getNonceHeader());
            if (signer.verifyV2(sign, request.getMethod(), request.getPath(),
                    request.getBodySha256(), request.getParams(), signedHeaders)) {
                return true;
            }
        }
        if (!appKeyProperties.isLegacySignatureEnabled()
                || !validTimestamp(request, appKeyProperties, appKeyProperties.isTimestampRequired())) {
            return false;
        }
        Map<String, String> params = new LinkedHashMap<>(request.getParams());
        putIfPresent(params, appKeyProperties.getAppKeyField(), AppKeySupport.resolveAppKey(request, appKeyProperties));
        putIfPresent(params, "method", request.getMethod());
        putIfPresent(params, appKeyProperties.getTimestampHeader(), request.header(appKeyProperties.getTimestampHeader()));
        putIfPresent(params, appKeyProperties.getNonceHeader(), request.header(appKeyProperties.getNonceHeader()));
        putIfPresent(params, appKeyProperties.getSignField(), resolveSign(request, appKeyProperties));
        if ("md5".equalsIgnoreCase(appKeyProperties.getAlgorithm())) {
            return signer.verifyMd5(params);
        }
        return signer.verifySha1(params);
    }

    private static void putHeader(Map<String, String> headers, AuthRequest request, String name) {
        if (AppKeySupport.hasText(name) && request.header(name) != null) {
            headers.put(name.toLowerCase(java.util.Locale.ROOT), request.header(name));
        }
    }

    private static void putIfPresent(Map<String, String> params, String key, String value) {
        if (AppKeySupport.hasText(key) && AppKeySupport.hasText(value)) {
            params.putIfAbsent(key, value);
        }
    }

    private static String resolveSign(AuthRequest request, KsetAuthProperties.AppKey properties) {
        String sign = request.header(properties.getSignHeader());
        if (!AppKeySupport.hasText(sign)) {
            sign = request.param(properties.getSignField());
        }
        return sign;
    }

    private static boolean validTimestamp(AuthRequest request, KsetAuthProperties.AppKey properties,
                                          boolean required) {
        String timestamp = request.header(properties.getTimestampHeader());
        if (!AppKeySupport.hasText(timestamp)) {
            timestamp = request.param(properties.getTimestampHeader());
        }
        if (!AppKeySupport.hasText(timestamp)) {
            return !required;
        }
        try {
            long value = Long.parseLong(timestamp.trim());
            if (value < 0) {
                return false;
            }
            long millis = value < 10_000_000_000L ? value * 1000L : value;
            Duration ttl = properties.getTimestampTtl() != null ? properties.getTimestampTtl() : Duration.ofMinutes(5);
            if (ttl.isNegative()) {
                return false;
            }
            return Math.abs(System.currentTimeMillis() - millis) <= ttl.toMillis();
        } catch (NumberFormatException ex) {
            return false;
        }
    }
}
