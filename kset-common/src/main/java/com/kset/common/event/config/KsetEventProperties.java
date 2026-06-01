package com.kset.common.event.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kset.event")
public class KsetEventProperties {

    private boolean enabled = true;
    /**
     * Optional TaskExecutor bean name used by the local Spring event facade.
     */
    private String executorBeanName;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getExecutorBeanName() {
        return executorBeanName;
    }

    public void setExecutorBeanName(String executorBeanName) {
        this.executorBeanName = executorBeanName;
    }
}
