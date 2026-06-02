package com.kset.common.context;

import com.kset.common.auth.LoginUser;

public final class KsetContextKeys {

    public static final KsetContextKey<LoginUser> LOGIN_USER =
            KsetContextKey.of("loginUser", LoginUser.class, true, true);
    public static final KsetContextKey<String> TRACE_ID =
            KsetContextKey.of("traceId", String.class);
    public static final KsetContextKey<String> SPAN_ID =
            KsetContextKey.of("spanId", String.class);
    public static final KsetContextKey<String> GRAY_TAG =
            KsetContextKey.of("grayTag", String.class);
    public static final KsetContextKey<String> TENANT_ID =
            KsetContextKey.of("tenantId", String.class);
    public static final KsetContextKey<String> LANGUAGE =
            KsetContextKey.of("language", String.class);

    private KsetContextKeys() {
    }
}
