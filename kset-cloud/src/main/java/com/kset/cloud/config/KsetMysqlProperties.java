package com.kset.cloud.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MySQL / MyBatis-Plus 约定配置（由 starter-mysql 消费）。
 */
@ConfigurationProperties(prefix = "kset.mysql")
public class KsetMysqlProperties {

    private boolean enabled = true;
    private boolean autoFill = true;
    private String logicDeleteField = "deleted";
    private int logicDeleteValue = 1;
    private int logicNotDeleteValue = 0;
    private final Flyway flyway = new Flyway();

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

    public String getLogicDeleteField() {
        return logicDeleteField;
    }

    public void setLogicDeleteField(String logicDeleteField) {
        this.logicDeleteField = logicDeleteField;
    }

    public int getLogicDeleteValue() {
        return logicDeleteValue;
    }

    public void setLogicDeleteValue(int logicDeleteValue) {
        this.logicDeleteValue = logicDeleteValue;
    }

    public int getLogicNotDeleteValue() {
        return logicNotDeleteValue;
    }

    public void setLogicNotDeleteValue(int logicNotDeleteValue) {
        this.logicNotDeleteValue = logicNotDeleteValue;
    }

    public Flyway getFlyway() {
        return flyway;
    }

    public static class Flyway {
        private boolean enabled = true;
        private String locations = "classpath:db/migration";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getLocations() {
            return locations;
        }

        public void setLocations(String locations) {
            this.locations = locations;
        }
    }
}
