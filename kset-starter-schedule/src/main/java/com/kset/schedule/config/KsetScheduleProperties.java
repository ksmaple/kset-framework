package com.kset.schedule.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * kset 定时任务配置。
 */
@ConfigurationProperties(prefix = "kset.scheduler")
public class KsetScheduleProperties {

    private final Lock lock = new Lock();

    public Lock getLock() {
        return lock;
    }

    public static class Lock {

        /**
         * 是否启用唯一运行锁。
         */
        private boolean enabled = true;

        /**
         * 锁表名。
         */
        private String tableName = "t_kset_schedule_lock";

        /**
         * 宕机兜底释放时间全局默认值（{@link com.kset.schedule.annotation.KsetTaskLock#atMostFor()} 为空时生效）。
         */
        private Duration atMostFor = Duration.ofMinutes(10);

        /**
         * 启动时自动建锁表（CREATE TABLE IF NOT EXISTS）。
         */
        private boolean autoCreateTable = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getTableName() {
            return tableName;
        }

        public void setTableName(String tableName) {
            this.tableName = tableName;
        }

        public Duration getAtMostFor() {
            return atMostFor;
        }

        public void setAtMostFor(Duration atMostFor) {
            this.atMostFor = atMostFor;
        }

        public boolean isAutoCreateTable() {
            return autoCreateTable;
        }

        public void setAutoCreateTable(boolean autoCreateTable) {
            this.autoCreateTable = autoCreateTable;
        }
    }
}
