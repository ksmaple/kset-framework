package com.kset.common.monitor.autoconfigure;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Provides conservative Actuator health defaults for applications using kset-starter-monitor.
 */
public class KsetHealthEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String PROPERTY_SOURCE_NAME = "ksetHealthDefaults";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> defaults = new LinkedHashMap<>();
        putIfMissing(environment, defaults, "management.endpoints.web.exposure.include", "health");
        putIfMissing(environment, defaults, "management.endpoint.health.probes.enabled", true);
        putIfMissing(environment, defaults, "management.endpoint.health.show-details", "never");
        putIfMissing(environment, defaults, "management.health.livenessstate.enabled", true);
        putIfMissing(environment, defaults, "management.health.readinessstate.enabled", true);

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

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
