package com.kset.common.logging.config;

import org.springframework.boot.context.properties.ConfigurationProperties;


@ConfigurationProperties(prefix = "kset.logging")
public class KsetLoggingProperties {

    
    public static final String DEFAULT_CONFIG_LOCATION = "classpath:kset-logback-spring.xml";

    /** 未显式指定 profile 时的默认环境 */
    public static final String DEFAULT_PROFILE = "dev";

    /** 业务侧 DEBUG 默认开关 */
    public static final boolean DEFAULT_BUSINESS_DEBUG_ENABLED = true;

    /**
     * 是否自动注入 {@code logging.config} 与默认 profile。
     * 设为 {@code false} 时回退 Spring Boot 默认 logback 发现逻辑。
     */
    private boolean autoConfig = true;

    /** 未设置 spring.profiles.active/default 时使用的默认 profile（等同 spring.profiles.default 缺省值） */
    private String defaultProfile = DEFAULT_PROFILE;

    /** 业务侧 DEBUG 配置 */
    private BusinessDebug businessDebug = new BusinessDebug();

    public boolean isAutoConfig() {
        return autoConfig;
    }

    public void setAutoConfig(boolean autoConfig) {
        this.autoConfig = autoConfig;
    }

    public String getDefaultProfile() {
        return defaultProfile;
    }

    public void setDefaultProfile(String defaultProfile) {
        this.defaultProfile = defaultProfile;
    }

    public BusinessDebug getBusinessDebug() {
        return businessDebug;
    }

    public void setBusinessDebug(BusinessDebug businessDebug) {
        this.businessDebug = businessDebug;
    }

    public static class BusinessDebug {

        /** dev/default 默认开启；test/prod 由环境后处理器默认关闭 */
        private boolean enabled = DEFAULT_BUSINESS_DEBUG_ENABLED;

        /** 业务侧 Logger 包名，多个包名用英文逗号分隔 */
        private String packages;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getPackages() {
            return packages;
        }

        public void setPackages(String packages) {
            this.packages = packages;
        }
    }
}
