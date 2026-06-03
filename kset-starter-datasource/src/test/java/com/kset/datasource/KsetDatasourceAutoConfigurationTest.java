package com.kset.datasource;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.kset.datasource.autoconfigure.KsetDatasourceAutoConfiguration;
import com.kset.datasource.autoconfigure.KsetDatasourceEnvironmentPostProcessor;
import com.kset.datasource.handler.KsetMetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KsetDatasourceAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(KsetDatasourceAutoConfiguration.class));

    private final KsetDatasourceEnvironmentPostProcessor processor = new KsetDatasourceEnvironmentPostProcessor();

    @Test
    void registersMetaObjectHandlerByDefault() {
        contextRunner.run(context -> assertThat(context)
                .hasSingleBean(KsetMetaObjectHandler.class)
                .hasSingleBean(MetaObjectHandler.class));
    }

    @Test
    void canDisableAutoFill() {
        contextRunner
                .withPropertyValues("kset.datasource.auto-fill=false")
                .run(context -> assertThat(context).doesNotHaveBean(MetaObjectHandler.class));
    }

    @Test
    void respectsCustomMetaObjectHandler() {
        contextRunner
                .withUserConfiguration(CustomMetaObjectHandlerConfiguration.class)
                .run(context -> assertThat(context)
                        .hasSingleBean(MetaObjectHandler.class)
                        .doesNotHaveBean(KsetMetaObjectHandler.class));
    }

    @Test
    void appliesDatasourceDefaultsWhenMissing() {
        MockEnvironment environment = new MockEnvironment();
        processor.postProcessEnvironment(environment, new SpringApplication(Object.class));

        assertThat(environment.getProperty("mybatis-plus.global-config.db-config.logic-delete-field"))
                .isEqualTo("deleted");
        assertThat(environment.getProperty("mybatis-plus.global-config.db-config.logic-delete-value"))
                .isEqualTo("1");
        assertThat(environment.getProperty("mybatis-plus.global-config.db-config.logic-not-delete-value"))
                .isEqualTo("0");
        assertThat(environment.getProperty("spring.datasource.dynamic.enabled", Boolean.class))
                .isFalse();
    }

    @Test
    void keepsDynamicDatasourceEnabledWhenNamedDatasourceExists() {
        MockEnvironment environment = new MockEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("testDynamicDatasource", Map.of(
                "spring.datasource.dynamic.datasource.master.url", "jdbc:h2:mem:master"
        )));
        processor.postProcessEnvironment(environment, new SpringApplication(Object.class));

        assertThat(environment.getProperty("spring.datasource.dynamic.enabled")).isNull();
    }

    @Test
    void respectsExplicitDynamicDatasourceSwitch() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("spring.datasource.dynamic.enabled", "true");
        processor.postProcessEnvironment(environment, new SpringApplication(Object.class));

        assertThat(environment.getProperty("spring.datasource.dynamic.enabled", Boolean.class)).isTrue();
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomMetaObjectHandlerConfiguration {

        @Bean
        MetaObjectHandler customMetaObjectHandler() {
            return new MetaObjectHandler() {
                @Override
                public void insertFill(MetaObject metaObject) {
                }

                @Override
                public void updateFill(MetaObject metaObject) {
                }
            };
        }
    }
}
