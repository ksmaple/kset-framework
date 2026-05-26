package com.kset.web.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kset.web")
public class KsetWebProperties {

    private final Oplog oplog = new Oplog();
    private final Knife4j knife4j = new Knife4j();
    private final RequestLogging requestLogging = new RequestLogging();

    public Oplog getOplog() {
        return oplog;
    }

    public Knife4j getKnife4j() {
        return knife4j;
    }

    public RequestLogging getRequestLogging() {
        return requestLogging;
    }

    public static class Oplog {
        private boolean enabled = true;
        private String userIdHeader = "X-User-Id";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getUserIdHeader() {
            return userIdHeader;
        }

        public void setUserIdHeader(String userIdHeader) {
            this.userIdHeader = userIdHeader;
        }
    }

    public static class Knife4j {
        private boolean enabled = true;
        /** 文档标题，默认取 spring.application.name */
        private String title;
        private String description = "KSet API";
        private String version = "1.0.0";
        /** OpenAPI 分组路径，默认 /api/** */
        private String pathPattern = "/api/**";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public String getPathPattern() {
            return pathPattern;
        }

        public void setPathPattern(String pathPattern) {
            this.pathPattern = pathPattern;
        }
    }

    public static class RequestLogging {
        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
