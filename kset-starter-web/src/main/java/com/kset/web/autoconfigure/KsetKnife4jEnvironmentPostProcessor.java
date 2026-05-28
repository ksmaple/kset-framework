package com.kset.web.autoconfigure;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Knife4j / OpenAPI 标准配置补全。
 *
 * <ul>
 *   <li>未设置 {@code knife4j.enable} 时默认 {@code true}</li>
 *   <li>兼容旧键 {@code kset.web.knife4j.enabled}（仅当 {@code knife4j.enable} 未显式配置时同步）</li>
 * </ul>
 */
public class KsetKnife4jEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String LEGACY_ENABLED = "kset.web.knife4j.enabled";
    private static final String KNIFE4J_ENABLE = "knife4j.enable";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> defaults = new LinkedHashMap<>();
        if (!environment.containsProperty(KNIFE4J_ENABLE)) {
            if (environment.containsProperty(LEGACY_ENABLED)) {
                Boolean legacy = environment.getProperty(LEGACY_ENABLED, Boolean.class);
                if (legacy != null) {
                    defaults.put(KNIFE4J_ENABLE, legacy);
                }
            } else {
                defaults.put(KNIFE4J_ENABLE, true);
            }
        }
        if (!defaults.isEmpty()) {
            environment.getPropertySources().addLast(new MapPropertySource("ksetKnife4jDefaults", defaults));
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
