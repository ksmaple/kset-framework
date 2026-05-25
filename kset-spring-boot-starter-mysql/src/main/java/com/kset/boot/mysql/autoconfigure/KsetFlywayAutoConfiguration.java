package com.kset.boot.mysql.autoconfigure;

import com.kset.cloud.config.KsetMysqlProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@AutoConfiguration(before = FlywayAutoConfiguration.class)
@ConditionalOnClass(name = "org.flywaydb.core.Flyway")
@ConditionalOnProperty(prefix = "kset.mysql.flyway", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(KsetMysqlProperties.class)
public class KsetFlywayAutoConfiguration {
}
