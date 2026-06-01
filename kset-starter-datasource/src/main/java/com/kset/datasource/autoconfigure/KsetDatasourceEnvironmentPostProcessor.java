package com.kset.datasource.autoconfigure;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 补全数据源组件默认值。
 */
public class KsetDatasourceEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String PROPERTY_SOURCE_NAME = "ksetDatasourceDefaults";
    private static final String DYNAMIC_ENABLED_KEY = "spring.datasource.dynamic.enabled";
    private static final String DYNAMIC_DATASOURCE_PREFIX = "spring.datasource.dynamic.datasource.";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> defaults = new LinkedHashMap<>();
        putIfMissing(environment, defaults, "mybatis-plus.global-config.db-config.logic-delete-field", "deleted");
        putIfMissing(environment, defaults, "mybatis-plus.global-config.db-config.logic-delete-value", "1");
        putIfMissing(environment, defaults, "mybatis-plus.global-config.db-config.logic-not-delete-value", "0");
        putDynamicDatasourceDefault(environment, defaults);
        if (!defaults.isEmpty()) {
            environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, defaults));
        }
    }

    private static void putIfMissing(ConfigurableEnvironment environment, Map<String, Object> defaults,
                                     String key, Object value) {
        if (!environment.containsProperty(key)) {
            defaults.put(key, value);
        }
    }

    private static void putDynamicDatasourceDefault(ConfigurableEnvironment environment, Map<String, Object> defaults) {
        if (environment.containsProperty(DYNAMIC_ENABLED_KEY) || hasPropertyWithPrefix(environment, DYNAMIC_DATASOURCE_PREFIX)) {
            return;
        }
        defaults.put(DYNAMIC_ENABLED_KEY, false);
    }

    private static boolean hasPropertyWithPrefix(ConfigurableEnvironment environment, String prefix) {
        for (PropertySource<?> propertySource : environment.getPropertySources()) {
            if (!(propertySource instanceof EnumerablePropertySource<?> enumerablePropertySource)) {
                continue;
            }
            for (String propertyName : enumerablePropertySource.getPropertyNames()) {
                if (propertyName.startsWith(prefix)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
