package com.kset.mysql.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SQL 慢查询监控（无 Spring Boot 标准等价项，KSet 扩展）。
 */
@ConfigurationProperties(prefix = "kset.mysql.slow-sql")
public class KsetMysqlMonitorProperties {

    private boolean enabled = true;
    private long thresholdMs = 200;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getThresholdMs() {
        return thresholdMs;
    }

    public void setThresholdMs(long thresholdMs) {
        this.thresholdMs = thresholdMs;
    }
}
