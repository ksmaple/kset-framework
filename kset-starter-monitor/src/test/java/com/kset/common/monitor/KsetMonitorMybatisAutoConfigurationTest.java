package com.kset.common.monitor;

import com.kset.common.monitor.autoconfigure.KsetMonitorMybatisAutoConfiguration;
import com.kset.common.monitor.interceptor.MybatisMonitorInterceptor;
import org.apache.ibatis.plugin.Interceptor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class KsetMonitorMybatisAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(KsetMonitorMybatisAutoConfiguration.class));

    @Test
    void registersMybatisMonitorInterceptorByDefault() {
        contextRunner.run(context -> assertThat(context)
                .hasSingleBean(MybatisMonitorInterceptor.class)
                .hasSingleBean(Interceptor.class));
    }

    @Test
    void canDisableMybatisMonitorInterceptor() {
        contextRunner
                .withPropertyValues("kset.monitor.mybatis.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(MybatisMonitorInterceptor.class));
    }

    @Test
    void canDisableAllMonitorAutoConfiguration() {
        contextRunner
                .withPropertyValues("kset.monitor.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(MybatisMonitorInterceptor.class));
    }

    @Test
    void respectsCustomMybatisMonitorInterceptor() {
        contextRunner
                .withUserConfiguration(CustomMybatisMonitorConfiguration.class)
                .run(context -> assertThat(context).hasSingleBean(MybatisMonitorInterceptor.class));
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomMybatisMonitorConfiguration {

        @Bean
        MybatisMonitorInterceptor customMybatisMonitorInterceptor() {
            return new MybatisMonitorInterceptor();
        }
    }
}
