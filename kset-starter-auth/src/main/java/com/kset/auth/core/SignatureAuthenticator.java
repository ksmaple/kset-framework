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
        if (!validTimestamp(request, appKeyProperties)) {
            return false;
        }
        Map<String, String> params = new LinkedHashMap<>(request.getParams());
        putIfPresent(params, appKeyProperties.getAppKeyField(), AppKeySupport.resolveAppKey(request, appKeyProperties));
        putIfPresent(params, "method", request.getMethod());
        putIfPresent(params, appKeyProperties.getTimestampHeader(), request.header(appKeyProperties.getTimestampHeader()));
        putIfPresent(params, appKeyProperties.getNonceHeader(), request.header(appKeyProperties.getNonceHeader()));
        putIfPresent(params, appKeyProperties.getSignField(), resolveSign(request, appKeyProperties));
        KsetSignUtil signer = KsetSignUtil.of(app.getSecret(), appKeyProperties.getSignField());
        if ("md5".equalsIgnoreCase(appKeyProperties.getAlgorithm())) {
            return signer.verifyMd5(params);
        }
        return signer.verifySha1(params);
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

    private static boolean validTimestamp(AuthRequest request, KsetAuthProperties.AppKey properties) {
        String timestamp = request.header(properties.getTimestampHeader());
        if (!AppKeySupport.hasText(timestamp)) {
            timestamp = request.param(properties.getTimestampHeader());
        }
        if (!AppKeySupport.hasText(timestamp)) {
            return !properties.isTimestampRequired();
        }
        try {
            long value = Long.parseLong(timestamp.trim());
            long millis = value < 10_000_000_000L ? value * 1000L : value;
            Duration ttl = properties.getTimestampTtl() != null ? properties.getTimestampTtl() : Duration.ofMinutes(5);
            return Math.abs(System.currentTimeMillis() - millis) <= ttl.toMillis();
        } catch (NumberFormatException ex) {
            return false;
        }
    }
}
