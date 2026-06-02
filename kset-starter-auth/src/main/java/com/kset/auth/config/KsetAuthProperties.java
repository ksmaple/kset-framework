package com.kset.auth.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.kset.common.auth.AuthHeaders;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kset.auth")
public class KsetAuthProperties {

    private boolean enabled = true;
    private String defaultSubject = "app";
    private String defaultScheme = "session";
    private String defaultTokenHeader = AuthHeaders.SESSION_TOKEN;
    private String subjectHeader = AuthHeaders.AUTH_SUBJECT;
    private List<AuthRule> rules = new ArrayList<>();
    private final Session session = new Session();
    private final AppKey appKey = new AppKey();
    private final Web web = new Web();
    private final Gateway gateway = new Gateway();
    private final Dubbo dubbo = new Dubbo();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDefaultSubject() {
        return defaultSubject;
    }

    public void setDefaultSubject(String defaultSubject) {
        this.defaultSubject = defaultSubject;
    }

    public String getDefaultScheme() {
        return defaultScheme;
    }

    public void setDefaultScheme(String defaultScheme) {
        this.defaultScheme = defaultScheme;
    }

    public String getDefaultTokenHeader() {
        return defaultTokenHeader;
    }

    public void setDefaultTokenHeader(String defaultTokenHeader) {
        this.defaultTokenHeader = defaultTokenHeader;
    }

    public String getSubjectHeader() {
        return subjectHeader;
    }

    public void setSubjectHeader(String subjectHeader) {
        this.subjectHeader = subjectHeader;
    }

    public List<AuthRule> getRules() {
        return rules;
    }

    public void setRules(List<AuthRule> rules) {
        this.rules = rules != null ? new ArrayList<>(rules) : new ArrayList<>();
    }

    public Session getSession() {
        return session;
    }

    public AppKey getAppKey() {
        return appKey;
    }

    public Web getWeb() {
        return web;
    }

    public Gateway getGateway() {
        return gateway;
    }

    public Dubbo getDubbo() {
        return dubbo;
    }

    public static class Session {
        private String keyPrefix = "kset:auth:session:";
        private Duration ttl = Duration.ofHours(2);
        private boolean slidingRefreshEnabled = true;
        private Duration refreshThreshold = Duration.ofMinutes(30);

        public String getKeyPrefix() {
            return keyPrefix;
        }

        public void setKeyPrefix(String keyPrefix) {
            this.keyPrefix = keyPrefix;
        }

        public Duration getTtl() {
            return ttl;
        }

        public void setTtl(Duration ttl) {
            this.ttl = ttl;
        }

        public boolean isSlidingRefreshEnabled() {
            return slidingRefreshEnabled;
        }

        public void setSlidingRefreshEnabled(boolean slidingRefreshEnabled) {
            this.slidingRefreshEnabled = slidingRefreshEnabled;
        }

        public Duration getRefreshThreshold() {
            return refreshThreshold;
        }

        public void setRefreshThreshold(Duration refreshThreshold) {
            this.refreshThreshold = refreshThreshold;
        }
    }

    public static class AppKey {
        private boolean enabled = true;
        private String appKeyHeader = "X-App-Key";
        private String tokenHeader = "X-App-Token";
        private String signHeader = "X-Sign";
        private String timestampHeader = "X-Timestamp";
        private String nonceHeader = "X-Nonce";
        private String appKeyField = "appKey";
        private String signField = "sign";
        private String algorithm = "sha1";
        private boolean timestampRequired = false;
        private Duration timestampTtl = Duration.ofMinutes(5);
        private List<App> apps = new ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getAppKeyHeader() {
            return appKeyHeader;
        }

        public void setAppKeyHeader(String appKeyHeader) {
            this.appKeyHeader = appKeyHeader;
        }

        public String getTokenHeader() {
            return tokenHeader;
        }

        public void setTokenHeader(String tokenHeader) {
            this.tokenHeader = tokenHeader;
        }

        public String getSignHeader() {
            return signHeader;
        }

        public void setSignHeader(String signHeader) {
            this.signHeader = signHeader;
        }

        public String getTimestampHeader() {
            return timestampHeader;
        }

        public void setTimestampHeader(String timestampHeader) {
            this.timestampHeader = timestampHeader;
        }

        public String getNonceHeader() {
            return nonceHeader;
        }

        public void setNonceHeader(String nonceHeader) {
            this.nonceHeader = nonceHeader;
        }

        public String getAppKeyField() {
            return appKeyField;
        }

        public void setAppKeyField(String appKeyField) {
            this.appKeyField = appKeyField;
        }

        public String getSignField() {
            return signField;
        }

        public void setSignField(String signField) {
            this.signField = signField;
        }

        public String getAlgorithm() {
            return algorithm;
        }

        public void setAlgorithm(String algorithm) {
            this.algorithm = algorithm;
        }

        public boolean isTimestampRequired() {
            return timestampRequired;
        }

        public void setTimestampRequired(boolean timestampRequired) {
            this.timestampRequired = timestampRequired;
        }

        public Duration getTimestampTtl() {
            return timestampTtl;
        }

        public void setTimestampTtl(Duration timestampTtl) {
            this.timestampTtl = timestampTtl;
        }

        public List<App> getApps() {
            return apps;
        }

        public void setApps(List<App> apps) {
            this.apps = apps != null ? new ArrayList<>(apps) : new ArrayList<>();
        }

        public Optional<App> findApp(String appKey) {
            if (appKey == null || appKey.isBlank()) {
                return Optional.empty();
            }
            String value = appKey.trim();
            return apps.stream()
                    .filter(App::isEnabled)
                    .filter(app -> value.equals(app.getAppKey()))
                    .findFirst();
        }
    }

    public static class App {
        private boolean enabled = true;
        private String appKey;
        private String secret;
        private String token;
        private String subject;
        private String userId;
        private String userName;
        private List<String> roles = new ArrayList<>();
        private List<String> permissions = new ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getAppKey() {
            return appKey;
        }

        public void setAppKey(String appKey) {
            this.appKey = appKey;
        }

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public String getSubject() {
            return subject;
        }

        public void setSubject(String subject) {
            this.subject = subject;
        }

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getUserName() {
            return userName;
        }

        public void setUserName(String userName) {
            this.userName = userName;
        }

        public List<String> getRoles() {
            return roles;
        }

        public void setRoles(List<String> roles) {
            this.roles = roles != null ? new ArrayList<>(roles) : new ArrayList<>();
        }

        public List<String> getPermissions() {
            return permissions;
        }

        public void setPermissions(List<String> permissions) {
            this.permissions = permissions != null ? new ArrayList<>(permissions) : new ArrayList<>();
        }
    }

    public static class Web {
        private boolean enabled = true;
        private Mode mode = Mode.REDIS;
        private String tokenHeader = AuthHeaders.SESSION_TOKEN;
        private List<String> publicPaths = new ArrayList<>(List.of(
                "/api/public/**",
                "/actuator/health/**",
                "/doc.html",
                "/v3/api-docs/**"));

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Mode getMode() {
            return mode;
        }

        public void setMode(Mode mode) {
            this.mode = mode;
        }

        public String getTokenHeader() {
            return tokenHeader;
        }

        public void setTokenHeader(String tokenHeader) {
            this.tokenHeader = tokenHeader;
        }

        public List<String> getPublicPaths() {
            return publicPaths;
        }

        public void setPublicPaths(List<String> publicPaths) {
            this.publicPaths = publicPaths != null ? new ArrayList<>(publicPaths) : new ArrayList<>();
        }
    }

    public static class Gateway {
        private boolean enabled = true;
        private String tokenHeader = AuthHeaders.SESSION_TOKEN;
        private List<String> publicPaths = new ArrayList<>(List.of(
                "/api/public/**",
                "/actuator/health/**",
                "/doc.html",
                "/v3/api-docs/**"));

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getTokenHeader() {
            return tokenHeader;
        }

        public void setTokenHeader(String tokenHeader) {
            this.tokenHeader = tokenHeader;
        }

        public List<String> getPublicPaths() {
            return publicPaths;
        }

        public void setPublicPaths(List<String> publicPaths) {
            this.publicPaths = publicPaths != null ? new ArrayList<>(publicPaths) : new ArrayList<>();
        }
    }

    public static class Dubbo {
        private boolean enabled = true;
        private boolean propagateToken = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isPropagateToken() {
            return propagateToken;
        }

        public void setPropagateToken(boolean propagateToken) {
            this.propagateToken = propagateToken;
        }
    }

    public static class AuthRule {
        private String name;
        private List<String> paths = new ArrayList<>();
        private String subject;
        private String scheme;
        private String tokenHeader;
        private String trustedHeaderName;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<String> getPaths() {
            return paths;
        }

        public void setPaths(List<String> paths) {
            this.paths = paths != null ? new ArrayList<>(paths) : new ArrayList<>();
        }

        public String getSubject() {
            return subject;
        }

        public void setSubject(String subject) {
            this.subject = subject;
        }

        public String getScheme() {
            return scheme;
        }

        public void setScheme(String scheme) {
            this.scheme = scheme;
        }

        public String getTokenHeader() {
            return tokenHeader;
        }

        public void setTokenHeader(String tokenHeader) {
            this.tokenHeader = tokenHeader;
        }

        public String getTrustedHeaderName() {
            return trustedHeaderName;
        }

        public void setTrustedHeaderName(String trustedHeaderName) {
            this.trustedHeaderName = trustedHeaderName;
        }
    }

    public enum Mode {
        REDIS,
        TRUSTED_HEADER
    }
}
