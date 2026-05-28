package com.kset.web.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kset.web")
public class KsetWebProperties {

    private final Oplog oplog = new Oplog();
    /** 可选覆盖；优先使用 {@code knife4j.*} / {@code springdoc.*} 标准配置 */
    private final Knife4j knife4j = new Knife4j();
    private final RequestLogging requestLogging = new RequestLogging();
    private final Response response = new Response();

    public Oplog getOplog() {
        return oplog;
    }

    public Knife4j getKnife4j() {
        return knife4j;
    }

    public RequestLogging getRequestLogging() {
        return requestLogging;
    }

    public Response getResponse() {
        return response;
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

    /**
     * OpenAPI 文档可选覆盖项；开关请用标准 {@code knife4j.enable}，扫描路径请用 {@code springdoc.group-configs}.
     */
    public static class Knife4j {
        private String title;
        private String description = "KSet API";
        private String version = "1.0.0";
        /** 未配置 springdoc.group-configs 时的默认扫描路径 */
        private String pathPattern = "/api/**";

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

    public static class Response {
        private boolean traceIdEnabled = true;

        public boolean isTraceIdEnabled() {
            return traceIdEnabled;
        }

        public void setTraceIdEnabled(boolean traceIdEnabled) {
            this.traceIdEnabled = traceIdEnabled;
        }
    }
}
