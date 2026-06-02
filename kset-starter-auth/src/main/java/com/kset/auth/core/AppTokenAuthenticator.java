package com.kset.auth.core;

import com.kset.auth.config.KsetAuthProperties;
import com.kset.auth.spi.Authenticator;

public class AppTokenAuthenticator implements Authenticator {

    private final KsetAuthProperties properties;

    public AppTokenAuthenticator(KsetAuthProperties properties) {
        this.properties = properties;
    }

    @Override
    public String scheme() {
        return AuthSchemes.APP_TOKEN;
    }

    @Override
    public AuthResult authenticate(AuthRequest request, AuthRuleMatch match) {
        KsetAuthProperties.AppKey appKeyProperties = properties.getAppKey();
        if (!appKeyProperties.isEnabled()) {
            return AuthResult.failure(401, "未登录");
        }
        String appKey = AppKeySupport.resolveAppKey(request, appKeyProperties);
        String token = request.header(match.getTokenHeader());
        return appKeyProperties.findApp(appKey)
                .filter(app -> AppKeySupport.hasText(app.getToken()))
                .filter(app -> app.getToken().equals(token))
                .map(app -> AuthResult.authenticated(AppKeySupport.buildUser(app, match.getSubject()).withToken(token)))
                .orElseGet(() -> AuthResult.failure(401, "未登录"));
    }
}
