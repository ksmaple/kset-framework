package com.kset.boot.web.autoconfigure;

import com.kset.boot.web.config.KsetWebProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Knife4j 条件装配（依赖 optional 的 knife4j starter）。
 */
@AutoConfiguration
@ConditionalOnClass(name = "com.github.xiaoymin.knife4j.spring.configuration.Knife4jAutoConfiguration")
@ConditionalOnProperty(prefix = "kset.web.knife4j", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(KsetWebProperties.class)
public class KsetKnife4jAutoConfiguration {
}
