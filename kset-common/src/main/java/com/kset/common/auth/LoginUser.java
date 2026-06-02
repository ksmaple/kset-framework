package com.kset.common.auth;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class LoginUser implements Serializable {

    private String userId;
    private String subjectType;
    private String userName;
    private String realName;
    private String nickName;
    private String avatar;
    private String mobile;
    private String email;
    private String tenantId;
    private String orgId;
    private String deptId;
    private List<String> roles = List.of();
    private List<String> permissions = List.of();
    private String token;
    private String deviceId;
    private String deviceType;
    private String clientType;
    private String clientVersion;
    private String ip;
    private String language;
    private String timeZone;
    private Long loginTime;
    private Map<String, String> extra = Map.of();

    public LoginUser() {
    }

    private LoginUser(Builder builder) {
        this.userId = builder.userId;
        this.subjectType = builder.subjectType;
        this.userName = builder.userName;
        this.realName = builder.realName;
        this.nickName = builder.nickName;
        this.avatar = builder.avatar;
        this.mobile = builder.mobile;
        this.email = builder.email;
        this.tenantId = builder.tenantId;
        this.orgId = builder.orgId;
        this.deptId = builder.deptId;
        this.roles = immutableList(builder.roles);
        this.permissions = immutableList(builder.permissions);
        this.token = builder.token;
        this.deviceId = builder.deviceId;
        this.deviceType = builder.deviceType;
        this.clientType = builder.clientType;
        this.clientVersion = builder.clientVersion;
        this.ip = builder.ip;
        this.language = builder.language;
        this.timeZone = builder.timeZone;
        this.loginTime = builder.loginTime;
        this.extra = immutableMap(builder.extra);
    }

    public static Builder builder() {
        return new Builder();
    }

    public LoginUser withToken(String token) {
        return builder()
                .userId(userId)
                .subjectType(subjectType)
                .userName(userName)
                .realName(realName)
                .nickName(nickName)
                .avatar(avatar)
                .mobile(mobile)
                .email(email)
                .tenantId(tenantId)
                .orgId(orgId)
                .deptId(deptId)
                .roles(roles)
                .permissions(permissions)
                .token(token)
                .deviceId(deviceId)
                .deviceType(deviceType)
                .clientType(clientType)
                .clientVersion(clientVersion)
                .ip(ip)
                .language(language)
                .timeZone(timeZone)
                .loginTime(loginTime)
                .extra(extra)
                .build();
    }

    public String getUserId() {
        return userId;
    }

    public String getSubjectType() {
        return subjectType;
    }

    public String getUserName() {
        return userName;
    }

    public String getRealName() {
        return realName;
    }

    public String getNickName() {
        return nickName;
    }

    public String getAvatar() {
        return avatar;
    }

    public String getMobile() {
        return mobile;
    }

    public String getEmail() {
        return email;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getOrgId() {
        return orgId;
    }

    public String getDeptId() {
        return deptId;
    }

    public List<String> getRoles() {
        return immutableList(roles);
    }

    public List<String> getPermissions() {
        return immutableList(permissions);
    }

    public String getToken() {
        return token;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public String getDeviceType() {
        return deviceType;
    }

    public String getClientType() {
        return clientType;
    }

    public String getClientVersion() {
        return clientVersion;
    }

    public String getIp() {
        return ip;
    }

    public String getLanguage() {
        return language;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public Long getLoginTime() {
        return loginTime;
    }

    public Map<String, String> getExtra() {
        return immutableMap(extra);
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public void setSubjectType(String subjectType) {
        this.subjectType = normalize(subjectType);
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public void setRealName(String realName) {
        this.realName = realName;
    }

    public void setNickName(String nickName) {
        this.nickName = nickName;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public void setOrgId(String orgId) {
        this.orgId = orgId;
    }

    public void setDeptId(String deptId) {
        this.deptId = deptId;
    }

    public void setRoles(List<String> roles) {
        this.roles = immutableList(roles);
    }

    public void setPermissions(List<String> permissions) {
        this.permissions = immutableList(permissions);
    }

    public void setToken(String token) {
        this.token = token;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public void setDeviceType(String deviceType) {
        this.deviceType = deviceType;
    }

    public void setClientType(String clientType) {
        this.clientType = clientType;
    }

    public void setClientVersion(String clientVersion) {
        this.clientVersion = clientVersion;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public void setTimeZone(String timeZone) {
        this.timeZone = timeZone;
    }

    public void setLoginTime(Long loginTime) {
        this.loginTime = loginTime;
    }

    public void setExtra(Map<String, String> extra) {
        this.extra = immutableMap(extra);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LoginUser loginUser)) {
            return false;
        }
        return Objects.equals(userId, loginUser.userId)
                && Objects.equals(subjectType, loginUser.subjectType)
                && Objects.equals(userName, loginUser.userName)
                && Objects.equals(realName, loginUser.realName)
                && Objects.equals(nickName, loginUser.nickName)
                && Objects.equals(avatar, loginUser.avatar)
                && Objects.equals(mobile, loginUser.mobile)
                && Objects.equals(email, loginUser.email)
                && Objects.equals(tenantId, loginUser.tenantId)
                && Objects.equals(orgId, loginUser.orgId)
                && Objects.equals(deptId, loginUser.deptId)
                && Objects.equals(roles, loginUser.roles)
                && Objects.equals(permissions, loginUser.permissions)
                && Objects.equals(token, loginUser.token)
                && Objects.equals(deviceId, loginUser.deviceId)
                && Objects.equals(deviceType, loginUser.deviceType)
                && Objects.equals(clientType, loginUser.clientType)
                && Objects.equals(clientVersion, loginUser.clientVersion)
                && Objects.equals(ip, loginUser.ip)
                && Objects.equals(language, loginUser.language)
                && Objects.equals(timeZone, loginUser.timeZone)
                && Objects.equals(loginTime, loginUser.loginTime)
                && Objects.equals(extra, loginUser.extra);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, subjectType, userName, realName, nickName, avatar, mobile, email,
                tenantId, orgId, deptId, roles, permissions, token, deviceId, deviceType,
                clientType, clientVersion, ip, language, timeZone, loginTime, extra);
    }

    public LoginUser withSubjectType(String subjectType) {
        return builder()
                .userId(userId)
                .subjectType(subjectType)
                .userName(userName)
                .realName(realName)
                .nickName(nickName)
                .avatar(avatar)
                .mobile(mobile)
                .email(email)
                .tenantId(tenantId)
                .orgId(orgId)
                .deptId(deptId)
                .roles(roles)
                .permissions(permissions)
                .token(token)
                .deviceId(deviceId)
                .deviceType(deviceType)
                .clientType(clientType)
                .clientVersion(clientVersion)
                .ip(ip)
                .language(language)
                .timeZone(timeZone)
                .loginTime(loginTime)
                .extra(extra)
                .build();
    }

    public boolean isSubject(String subjectType) {
        return !hasText(subjectType) || Objects.equals(normalize(this.subjectType), normalize(subjectType));
    }

    private static List<String> immutableList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private static Map<String, String> immutableMap(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Map<String, String> copy = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            if (key != null && !key.isBlank() && value != null) {
                copy.put(key, value);
            }
        });
        return Map.copyOf(copy);
    }

    private static String normalize(String value) {
        return hasText(value) ? value.trim().toLowerCase() : null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public static final class Builder {
        private String userId;
        private String subjectType;
        private String userName;
        private String realName;
        private String nickName;
        private String avatar;
        private String mobile;
        private String email;
        private String tenantId;
        private String orgId;
        private String deptId;
        private List<String> roles = List.of();
        private List<String> permissions = List.of();
        private String token;
        private String deviceId;
        private String deviceType;
        private String clientType;
        private String clientVersion;
        private String ip;
        private String language;
        private String timeZone;
        private Long loginTime;
        private Map<String, String> extra = Map.of();

        private Builder() {
        }

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder subjectType(String subjectType) {
            this.subjectType = normalize(subjectType);
            return this;
        }

        public Builder userName(String userName) {
            this.userName = userName;
            return this;
        }

        public Builder realName(String realName) {
            this.realName = realName;
            return this;
        }

        public Builder nickName(String nickName) {
            this.nickName = nickName;
            return this;
        }

        public Builder avatar(String avatar) {
            this.avatar = avatar;
            return this;
        }

        public Builder mobile(String mobile) {
            this.mobile = mobile;
            return this;
        }

        public Builder email(String email) {
            this.email = email;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder orgId(String orgId) {
            this.orgId = orgId;
            return this;
        }

        public Builder deptId(String deptId) {
            this.deptId = deptId;
            return this;
        }

        public Builder roles(List<String> roles) {
            this.roles = roles;
            return this;
        }

        public Builder permissions(List<String> permissions) {
            this.permissions = permissions;
            return this;
        }

        public Builder token(String token) {
            this.token = token;
            return this;
        }

        public Builder deviceId(String deviceId) {
            this.deviceId = deviceId;
            return this;
        }

        public Builder deviceType(String deviceType) {
            this.deviceType = deviceType;
            return this;
        }

        public Builder clientType(String clientType) {
            this.clientType = clientType;
            return this;
        }

        public Builder clientVersion(String clientVersion) {
            this.clientVersion = clientVersion;
            return this;
        }

        public Builder ip(String ip) {
            this.ip = ip;
            return this;
        }

        public Builder language(String language) {
            this.language = language;
            return this;
        }

        public Builder timeZone(String timeZone) {
            this.timeZone = timeZone;
            return this;
        }

        public Builder loginTime(Long loginTime) {
            this.loginTime = loginTime;
            return this;
        }

        public Builder extra(Map<String, String> extra) {
            this.extra = extra;
            return this;
        }

        public LoginUser build() {
            return new LoginUser(this);
        }
    }
}
