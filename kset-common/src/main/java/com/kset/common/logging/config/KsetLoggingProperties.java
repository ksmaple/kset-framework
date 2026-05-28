package com.kset.common.logging.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * KSet 框架专属日志开关（文件路径、级别、滚动策略等请用 Spring Boot 标准 {@code logging.*}）。
 *
 * <p>接入方依赖任意 {@code kset-starter-*}（经 {@code kset-common} 传递）即可继承统一 logback，
 * 无需在业务工程复制 {@code logback-spring.xml}。</p>
 *
 * @see org.springframework.boot.logging.LoggingSystemProperties
 */
@ConfigurationProperties(prefix = "kset.logging")
public class KsetLoggingProperties {

    /** classpath 内统一 logback 配置路径 */
    public static final String DEFAULT_CONFIG_LOCATION = "classpath:kset-logback-spring.xml";

    /** 未显式指定 profile 时的默认环境 */
    public static final String DEFAULT_PROFILE = "dev";

    /**
     * 是否自动注入 {@code logging.config} 与默认 profile。
     * 设为 {@code false} 时回退 Spring Boot 默认 logback 发现逻辑。
     */
    private boolean autoConfig = true;

    /** 未设置 spring.profiles.active/default 时使用的默认 profile（等同 spring.profiles.default 缺省值） */
    private String defaultProfile = DEFAULT_PROFILE;

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
}
