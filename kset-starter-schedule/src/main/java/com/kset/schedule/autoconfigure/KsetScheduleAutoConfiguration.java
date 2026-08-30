package com.kset.schedule.autoconfigure;

import com.kset.schedule.aop.KsetTaskLockAspect;
import com.kset.schedule.config.KsetScheduleProperties;
import com.kset.schedule.config.KsetTaskLockAnnotationValidator;
import com.kset.schedule.lock.JdbcTaskLockProvider;
import com.kset.schedule.lock.TaskLockProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * kset 定时任务装配：DataSource 存在时自动启用 SQL 唯一运行锁（零配置，自动建表）。
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "kset.scheduler.lock", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(KsetScheduleProperties.class)
public class KsetScheduleAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(KsetScheduleAutoConfiguration.class);

    @Bean
    @ConditionalOnClass(JdbcTemplate.class)
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnMissingBean
    public TaskLockProvider taskLockProvider(DataSource dataSource, KsetScheduleProperties properties) {
        JdbcTaskLockProvider provider = new JdbcTaskLockProvider(
                new JdbcTemplate(dataSource), properties.getLock().getTableName());
        if (properties.getLock().isAutoCreateTable()) {
            provider.createTableIfNotExists();
        }
        return provider;
    }

    @Bean
    @ConditionalOnBean(TaskLockProvider.class)
    @ConditionalOnMissingBean
    public KsetTaskLockAspect ksetTaskLockAspect(TaskLockProvider taskLockProvider,
                                                 KsetScheduleProperties properties) {
        log.info("[kset-schedule] 唯一运行锁已启用（SQL 锁表）");
        return new KsetTaskLockAspect(taskLockProvider, properties);
    }

    /**
     * 启动期校验 @KsetTaskLock 注解的时长格式，非法值 fail-fast。
     */
    @Bean
    @ConditionalOnMissingBean
    public KsetTaskLockAnnotationValidator ksetTaskLockAnnotationValidator(
            org.springframework.context.ApplicationContext applicationContext) {
        return new KsetTaskLockAnnotationValidator(applicationContext);
    }

    /**
     * 无 DataSource 时 @KsetTaskLock 不生效（单机语义），启动时明确告警。
     */
    @Bean
    @ConditionalOnMissingBean(TaskLockProvider.class)
    public ApplicationRunner ksetTaskLockMissingWarner() {
        return args -> log.warn("[kset-schedule] 未发现 DataSource，@KsetTaskLock 唯一运行锁不生效，任务将在每个实例执行");
    }
}
