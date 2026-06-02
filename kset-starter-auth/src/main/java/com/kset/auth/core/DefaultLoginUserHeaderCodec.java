package com.kset.auth.core;

import com.alibaba.fastjson2.JSON;
import com.kset.auth.spi.LoginUserHeaderCodec;
import com.kset.common.auth.AuthHeaders;
import com.kset.common.auth.LoginUser;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class DefaultLoginUserHeaderCodec implements LoginUserHeaderCodec {

    private static final String SEPARATOR = ",";

    @Override
    public Map<String, String> encode(LoginUser user, boolean includeToken) {
        if (user == null) {
            return Map.of();
        }
        String subject = subjectOrDefault(user.getSubjectType());
        Map<String, String> headers = new LinkedHashMap<>();
        put(headers, AuthHeaders.AUTH_SUBJECT, subject);
        put(headers, AuthHeaders.loginContextHeader(subject), JSON.toJSONString(BasicLoginContext.from(user, includeToken)));
        put(headers, AuthHeaders.DEVICE_ID, user.getDeviceId());
        put(headers, AuthHeaders.DEVICE_TYPE, user.getDeviceType());
        put(headers, AuthHeaders.CLIENT_TYPE, user.getClientType());
        put(headers, AuthHeaders.CLIENT_VERSION, user.getClientVersion());
        put(headers, AuthHeaders.IP, user.getIp());
        put(headers, AuthHeaders.LANGUAGE, user.getLanguage());
        put(headers, AuthHeaders.TIME_ZONE, user.getTimeZone());
        put(headers, AuthHeaders.LOGIN_TIME, user.getLoginTime() != null ? String.valueOf(user.getLoginTime()) : null);
        return headers;
    }

    @Override
    public Optional<LoginUser> decode(HeaderReader reader) {
        if (reader == null) {
            return Optional.empty();
        }
        String subject = subjectOrDefault(reader.get(AuthHeaders.AUTH_SUBJECT));
        Optional<LoginUser> contextUser = decodeLoginContext(reader, subject, AuthHeaders.loginContextHeader(subject));
        if (contextUser.isPresent()) {
            return contextUser;
        }
        contextUser = decodeLoginContext(reader, subject, AuthHeaders.LOGIN_CONTEXT);
        if (contextUser.isPresent()) {
            return contextUser;
        }
        return decodeLegacyHeaders(reader);
    }

    @Override
    public Optional<LoginUser> decode(HeaderReader reader, String subjectType, String headerName) {
        if (reader == null) {
            return Optional.empty();
        }
        String subject = subjectOrDefault(subjectType);
        String contextHeader = hasText(headerName) ? headerName : AuthHeaders.loginContextHeader(subject);
        Optional<LoginUser> contextUser = decodeLoginContext(reader, subject, contextHeader);
        if (contextUser.isPresent()) {
            return contextUser;
        }
        if (!AuthHeaders.LOGIN_CONTEXT.equals(contextHeader)) {
            contextUser = decodeLoginContext(reader, subject, AuthHeaders.LOGIN_CONTEXT);
            if (contextUser.isPresent()) {
                return contextUser;
            }
        }
        return decodeLegacyHeaders(reader).map(user -> user.withSubjectType(subject));
    }

    private Optional<LoginUser> decodeLoginContext(HeaderReader reader, String subject, String headerName) {
        String value = reader.get(headerName);
        if (!hasText(value)) {
            return Optional.empty();
        }
        try {
            BasicLoginContext context = JSON.parseObject(value, BasicLoginContext.class);
            if (context == null || !hasText(context.getUserId())) {
                return Optional.empty();
            }
            return Optional.of(LoginUser.builder()
                    .userId(context.getUserId())
                    .subjectType(subject)
                    .userName(context.getUserName())
                    .tenantId(context.getTenantId())
                    .orgId(context.getOrgId())
                    .deptId(context.getDeptId())
                    .roles(context.getRoles())
                    .permissions(context.getPermissions())
                    .token(context.getToken())
                    .deviceId(reader.get(AuthHeaders.DEVICE_ID))
                    .deviceType(reader.get(AuthHeaders.DEVICE_TYPE))
                    .clientType(reader.get(AuthHeaders.CLIENT_TYPE))
                    .clientVersion(reader.get(AuthHeaders.CLIENT_VERSION))
                    .ip(reader.get(AuthHeaders.IP))
                    .language(reader.get(AuthHeaders.LANGUAGE))
                    .timeZone(reader.get(AuthHeaders.TIME_ZONE))
                    .loginTime(parseLong(reader.get(AuthHeaders.LOGIN_TIME)))
                    .build());
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private Optional<LoginUser> decodeLegacyHeaders(HeaderReader reader) {
        if (!hasText(reader.get(AuthHeaders.USER_ID))) {
            return Optional.empty();
        }
        return Optional.of(LoginUser.builder()
                .userId(reader.get(AuthHeaders.USER_ID))
                .subjectType(reader.get(AuthHeaders.AUTH_SUBJECT))
                .userName(reader.get(AuthHeaders.USER_NAME))
                .realName(reader.get(AuthHeaders.REAL_NAME))
                .nickName(reader.get(AuthHeaders.NICK_NAME))
                .avatar(reader.get(AuthHeaders.AVATAR))
                .mobile(reader.get(AuthHeaders.MOBILE))
                .email(reader.get(AuthHeaders.EMAIL))
                .tenantId(reader.get(AuthHeaders.TENANT_ID))
                .orgId(reader.get(AuthHeaders.ORG_ID))
                .deptId(reader.get(AuthHeaders.DEPT_ID))
                .roles(split(reader.get(AuthHeaders.ROLES)))
                .permissions(split(reader.get(AuthHeaders.PERMISSIONS)))
                .deviceId(reader.get(AuthHeaders.DEVICE_ID))
                .deviceType(reader.get(AuthHeaders.DEVICE_TYPE))
                .clientType(reader.get(AuthHeaders.CLIENT_TYPE))
                .clientVersion(reader.get(AuthHeaders.CLIENT_VERSION))
                .ip(reader.get(AuthHeaders.IP))
                .language(reader.get(AuthHeaders.LANGUAGE))
                .timeZone(reader.get(AuthHeaders.TIME_ZONE))
                .loginTime(parseLong(reader.get(AuthHeaders.LOGIN_TIME)))
                .token(reader.get(AuthHeaders.SESSION_TOKEN))
                .build());
    }

    @Deprecated
    private Map<String, String> encodeLegacyHeaders(LoginUser user, boolean includeToken) {
        Map<String, String> headers = new LinkedHashMap<>();
        put(headers, AuthHeaders.USER_ID, user.getUserId());
        put(headers, AuthHeaders.USER_NAME, user.getUserName());
        put(headers, AuthHeaders.REAL_NAME, user.getRealName());
        put(headers, AuthHeaders.NICK_NAME, user.getNickName());
        put(headers, AuthHeaders.AVATAR, user.getAvatar());
        put(headers, AuthHeaders.MOBILE, user.getMobile());
        put(headers, AuthHeaders.EMAIL, user.getEmail());
        put(headers, AuthHeaders.TENANT_ID, user.getTenantId());
        put(headers, AuthHeaders.ORG_ID, user.getOrgId());
        put(headers, AuthHeaders.DEPT_ID, user.getDeptId());
        put(headers, AuthHeaders.ROLES, String.join(SEPARATOR, user.getRoles()));
        put(headers, AuthHeaders.PERMISSIONS, String.join(SEPARATOR, user.getPermissions()));
        put(headers, AuthHeaders.DEVICE_ID, user.getDeviceId());
        put(headers, AuthHeaders.DEVICE_TYPE, user.getDeviceType());
        put(headers, AuthHeaders.CLIENT_TYPE, user.getClientType());
        put(headers, AuthHeaders.CLIENT_VERSION, user.getClientVersion());
        put(headers, AuthHeaders.IP, user.getIp());
        put(headers, AuthHeaders.LANGUAGE, user.getLanguage());
        put(headers, AuthHeaders.TIME_ZONE, user.getTimeZone());
        put(headers, AuthHeaders.LOGIN_TIME, user.getLoginTime() != null ? String.valueOf(user.getLoginTime()) : null);
        if (includeToken) {
            put(headers, AuthHeaders.SESSION_TOKEN, user.getToken());
        }
        return headers;
    }

    private static void put(Map<String, String> headers, String key, String value) {
        if (hasText(value)) {
            headers.put(key, value);
        }
    }

    private static List<String> split(String value) {
        if (!hasText(value)) {
            return List.of();
        }
        return Arrays.stream(value.split(SEPARATOR))
                .map(String::trim)
                .filter(DefaultLoginUserHeaderCodec::hasText)
                .distinct()
                .toList();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String subjectOrDefault(String value) {
        return hasText(value) ? value.trim().toLowerCase() : "app";
    }

    private static Long parseLong(String value) {
        if (!hasText(value)) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static final class BasicLoginContext {
        private String userId;
        private String subjectType;
        private String userName;
        private String tenantId;
        private String orgId;
        private String deptId;
        private List<String> roles = List.of();
        private List<String> permissions = List.of();
        private String token;

        public static BasicLoginContext from(LoginUser user, boolean includeToken) {
            BasicLoginContext context = new BasicLoginContext();
            context.userId = user.getUserId();
            context.subjectType = user.getSubjectType();
            context.userName = user.getUserName();
            context.tenantId = user.getTenantId();
            context.orgId = user.getOrgId();
            context.deptId = user.getDeptId();
            context.roles = user.getRoles();
            context.permissions = user.getPermissions();
            context.token = includeToken ? user.getToken() : null;
            return context;
        }

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getSubjectType() {
            return subjectType;
        }

        public void setSubjectType(String subjectType) {
            this.subjectType = subjectType;
        }

        public String getUserName() {
            return userName;
        }

        public void setUserName(String userName) {
            this.userName = userName;
        }

        public String getTenantId() {
            return tenantId;
        }

        public void setTenantId(String tenantId) {
            this.tenantId = tenantId;
        }

        public String getOrgId() {
            return orgId;
        }

        public void setOrgId(String orgId) {
            this.orgId = orgId;
        }

        public String getDeptId() {
            return deptId;
        }

        public void setDeptId(String deptId) {
            this.deptId = deptId;
        }

        public List<String> getRoles() {
            return roles;
        }

        public void setRoles(List<String> roles) {
            this.roles = roles != null ? roles : List.of();
        }

        public List<String> getPermissions() {
            return permissions;
        }

        public void setPermissions(List<String> permissions) {
            this.permissions = permissions != null ? permissions : List.of();
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }
    }
}
