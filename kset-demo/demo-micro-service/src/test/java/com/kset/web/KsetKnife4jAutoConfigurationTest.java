package com.kset.web;

import com.kset.web.autoconfigure.KsetKnife4jAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class KsetKnife4jAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(KsetKnife4jAutoConfiguration.class));

    @Test
    void usesDefaultApiPathWhenMissing() {
        contextRunner.run(context -> assertThat(context.getBean(GroupedOpenApi.class).getPathsToMatch())
                .containsExactly("/api/**"));
    }

    @Test
    void usesCustomPathList() {
        contextRunner
                .withPropertyValues(
                        "springdoc.group-configs[0].paths-to-match[0]=/api/**",
                        "springdoc.group-configs[0].paths-to-match[1]=/custom/**")
                .run(context -> assertThat(context.getBean(GroupedOpenApi.class).getPathsToMatch())
                        .containsExactly("/api/**", "/custom/**"));
    }

    @Test
    void usesCustomCommaDelimitedPaths() {
        contextRunner
                .withPropertyValues("springdoc.group-configs[0].paths-to-match=/api/**,/custom/**")
                .run(context -> assertThat(context.getBean(GroupedOpenApi.class).getPathsToMatch())
                        .containsExactly("/api/**", "/custom/**"));
    }

    @Test
    void respectsCustomGroupedOpenApi() {
        contextRunner
                .withUserConfiguration(CustomGroupedOpenApiConfiguration.class)
                .run(context -> assertThat(context.getBean(GroupedOpenApi.class).getGroup())
                        .isEqualTo("custom"));
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomGroupedOpenApiConfiguration {

        @Bean
        GroupedOpenApi customGroupedOpenApi() {
            return GroupedOpenApi.builder()
                    .group("custom")
                    .pathsToMatch("/custom/**")
                    .build();
        }
    }
}
