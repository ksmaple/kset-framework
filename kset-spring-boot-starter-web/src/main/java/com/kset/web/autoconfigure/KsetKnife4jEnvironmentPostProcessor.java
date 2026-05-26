package com.kset.web.autoconfigure;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 将 {@code kset.web.knife4j.enabled} 同步到 Knife4j 原生开关 {@code knife4j.enable}。
 */
public class KsetKnife4jEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String KSET_ENABLED = "kset.web.knife4j.enabled";
    private static final String KNIFE4J_ENABLE = "knife4j.enable";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!environment.containsProperty(KSET_ENABLED)) {
            return;
        }
        Boolean enabled = environment.getProperty(KSET_ENABLED, Boolean.class);
        if (enabled == null) {
            return;
        }
        Map<String, Object> defaults = new LinkedHashMap<>();
        putIfMissing(environment, defaults, KNIFE4J_ENABLE, enabled);
        if (!defaults.isEmpty()) {
            environment.getPropertySources().addLast(new MapPropertySource("ksetKnife4jDefaults", defaults));
        }
    }

    private static void putIfMissing(ConfigurableEnvironment environment, Map<String, Object> target,
                                     String key, Object value) {
        if (!environment.containsProperty(key)) {
            target.put(key, value);
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
