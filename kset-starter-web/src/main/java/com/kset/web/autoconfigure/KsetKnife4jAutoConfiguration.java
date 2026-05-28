package com.kset.web.autoconfigure;

import com.kset.web.config.KsetWebProperties;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * Knife4j / OpenAPI 3 自动配置（基于 knife4j-openapi3-jakarta-spring-boot-starter）。
 *
 * <p>开关使用 Knife4j 标准 {@code knife4j.enable}；分组路径优先 {@code springdoc.group-configs[0].paths-to-match}。</p>
 */
@AutoConfiguration
@ConditionalOnClass(name = "com.github.xiaoymin.knife4j.spring.configuration.Knife4jAutoConfiguration")
@ConditionalOnProperty(name = "knife4j.enable", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(KsetWebProperties.class)
public class KsetKnife4jAutoConfiguration {

    private static final String SPRINGDOC_PATH = "springdoc.group-configs[0].paths-to-match";

    @Bean
    @ConditionalOnMissingBean
    public OpenAPI ksetOpenApi(KsetWebProperties properties, Environment environment) {
        KsetWebProperties.Knife4j knife4j = properties.getKnife4j();
        String appName = environment.getProperty("spring.application.name", "application");
        String title = StringUtils.hasText(knife4j.getTitle()) ? knife4j.getTitle() : appName;
        return new OpenAPI()
                .info(new Info()
                        .title(title)
                        .description(knife4j.getDescription())
                        .version(knife4j.getVersion()));
    }

    @Bean
    @ConditionalOnMissingBean
    public GroupedOpenApi ksetDefaultGroupedOpenApi(KsetWebProperties properties, Environment environment) {
        String pathPattern = environment.getProperty(SPRINGDOC_PATH, knife4jPathPattern(properties));
        return GroupedOpenApi.builder()
                .group("default")
                .pathsToMatch(pathPattern)
                .build();
    }

    private static String knife4jPathPattern(KsetWebProperties properties) {
        return properties.getKnife4j().getPathPattern();
    }
}
