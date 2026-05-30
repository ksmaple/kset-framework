package com.kset.datasource.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;

@AutoConfiguration(before = FlywayAutoConfiguration.class)
@ConditionalOnClass(name = "org.flywaydb.core.Flyway")
@ConditionalOnProperty(prefix = "spring.flyway", name = "enabled", havingValue = "true", matchIfMissing = true)
public class KsetFlywayAutoConfiguration {
}
