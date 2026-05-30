package com.kset.cloud.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 数据源组件开关。
 *
 * <p>MyBatis-Plus、Flyway 和 dynamic-datasource 均优先使用开源组件原生配置：
 * {@code mybatis-plus.*}、{@code spring.flyway.*}、{@code spring.datasource.dynamic.*}。</p>
 */
@ConfigurationProperties(prefix = "kset.datasource")
public class KsetDatasourceProperties {

    private boolean enabled = true;
    /** 是否注册 createTime / updateTime 自动填充处理器。 */
    private boolean autoFill = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isAutoFill() {
        return autoFill;
    }

    public void setAutoFill(boolean autoFill) {
        this.autoFill = autoFill;
    }
}
