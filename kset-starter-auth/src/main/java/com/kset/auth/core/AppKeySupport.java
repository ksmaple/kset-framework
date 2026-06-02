package com.kset.auth.core;

import com.kset.auth.config.KsetAuthProperties;
import com.kset.common.auth.LoginUser;

final class AppKeySupport {

    private AppKeySupport() {
    }

    static String resolveAppKey(AuthRequest request, KsetAuthProperties.AppKey properties) {
        String appKey = request.header(properties.getAppKeyHeader());
        if (!hasText(appKey)) {
            appKey = request.param(properties.getAppKeyField());
        }
        return hasText(appKey) ? appKey.trim() : null;
    }

    static LoginUser buildUser(KsetAuthProperties.App app, String subject) {
        String userId = hasText(app.getUserId()) ? app.getUserId() : app.getAppKey();
        String subjectType = hasText(app.getSubject()) ? app.getSubject() : subject;
        return LoginUser.builder()
                .userId(userId)
                .subjectType(subjectType)
                .userName(app.getUserName())
                .roles(app.getRoles())
                .permissions(app.getPermissions())
                .build();
    }

    static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
