package com.kset.common.auth;

public final class AuthHeaders {

    public static final String SESSION_TOKEN = "X-Session-Token";
    public static final String AUTH_SUBJECT = "X-Auth-Subject";
    public static final String LOGIN_CONTEXT = "X-Login-Context";
    public static final String APP_LOGIN_CONTEXT = "X-App-Login-Context";
    public static final String ADMIN_LOGIN_CONTEXT = "X-Admin-Login-Context";
    public static final String USER_ID = "X-User-Id";
    public static final String USER_NAME = "X-User-Name";
    public static final String REAL_NAME = "X-Real-Name";
    public static final String NICK_NAME = "X-Nick-Name";
    public static final String AVATAR = "X-Avatar";
    public static final String MOBILE = "X-Mobile";
    public static final String EMAIL = "X-Email";
    public static final String TENANT_ID = "X-Tenant-Id";
    public static final String ORG_ID = "X-Org-Id";
    public static final String DEPT_ID = "X-Dept-Id";
    public static final String ROLES = "X-Roles";
    public static final String PERMISSIONS = "X-Permissions";
    public static final String DEVICE_ID = "X-Device-Id";
    public static final String DEVICE_TYPE = "X-Device-Type";
    public static final String CLIENT_TYPE = "X-Client-Type";
    public static final String CLIENT_VERSION = "X-Client-Version";
    public static final String IP = "X-Client-Ip";
    public static final String LANGUAGE = "X-Language";
    public static final String TIME_ZONE = "X-Time-Zone";
    public static final String LOGIN_TIME = "X-Login-Time";

    private AuthHeaders() {
    }

    public static String loginContextHeader(String subjectType) {
        if (subjectType == null || subjectType.isBlank()) {
            return APP_LOGIN_CONTEXT;
        }
        String subject = subjectType.trim();
        if ("app".equalsIgnoreCase(subject)) {
            return APP_LOGIN_CONTEXT;
        }
        if ("admin".equalsIgnoreCase(subject)) {
            return ADMIN_LOGIN_CONTEXT;
        }
        return "X-" + Character.toUpperCase(subject.charAt(0)) + subject.substring(1) + "-Login-Context";
    }
}
