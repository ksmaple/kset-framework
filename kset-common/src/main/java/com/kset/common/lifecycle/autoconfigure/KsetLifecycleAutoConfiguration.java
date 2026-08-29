package com.kset.common.lifecycle.autoconfigure;

import com.kset.common.lifecycle.AbstractKsetLifecycleComponent;
import com.kset.common.lifecycle.KsetLifecycleHealthIndicator;
import com.kset.common.lifecycle.KsetLifecycleProperties;
import com.kset.common.lifecycle.KsetReadinessLifecycle;
import com.kset.common.lifecycle.KsetThreadPoolLifecycle;
import com.kset.common.utils.thread.KsetThreadPoolFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * kset 统一启停装配：基于 Spring {@code SmartLifecycle} 相位编排全部 kset 组件的启停顺序。
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "kset.lifecycle", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(KsetLifecycleProperties.class)
public class KsetLifecycleAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public KsetThreadPoolLifecycle ksetThreadPoolLifecycle(KsetLifecycleProperties properties) {
        return new KsetThreadPoolLifecycle(KsetThreadPoolFactory.getInstance(), properties.getShutdownTimeout());
    }

    @Bean
    @ConditionalOnMissingBean
    public KsetReadinessLifecycle ksetReadinessLifecycle(ApplicationEventPublisher eventPublisher) {
        return new KsetReadinessLifecycle(eventPublisher);
    }

    /**
     * Actuator 健康指标：仅当业务侧引入 spring-boot-actuator 时装配。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.boot.actuate.health.HealthIndicator")
    @ConditionalOnProperty(prefix = "kset.lifecycle.health", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class KsetLifecycleHealthConfiguration {

        @Bean
        @ConditionalOnMissingBean
        KsetLifecycleHealthIndicator ksetLifecycleHealthIndicator(
                ObjectProvider<AbstractKsetLifecycleComponent> components) {
            return new KsetLifecycleHealthIndicator(components);
        }
    }
}
