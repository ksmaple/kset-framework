package com.kset.cloud.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MySQL / MyBatis-Plus 框架开关（逻辑删除、Flyway 等请用标准 {@code mybatis-plus.*} / {@code spring.flyway.*}）。
 *
 * <p>默认约定见 starter-mysql 的 {@code application-kset-mysql.yml}。</p>
 */
@ConfigurationProperties(prefix = "kset.mysql")
public class KsetMysqlProperties {

    private boolean enabled = true;
    /** 是否注册 createTime / updateTime 自动填充 */
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
