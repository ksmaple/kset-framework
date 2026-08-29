package com.kset.common.lifecycle;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * kset 统一启停配置。
 */
@ConfigurationProperties(prefix = "kset.lifecycle")
public class KsetLifecycleProperties {

    /**
     * 是否启用 kset 统一启停编排。
     */
    private boolean enabled = true;

    /**
     * 各组件停机排空等待预算，超时后兜底强制关闭。
     */
    private Duration shutdownTimeout = Duration.ofSeconds(30);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getShutdownTimeout() {
        return shutdownTimeout;
    }

    public void setShutdownTimeout(Duration shutdownTimeout) {
        this.shutdownTimeout = shutdownTimeout;
    }
}
