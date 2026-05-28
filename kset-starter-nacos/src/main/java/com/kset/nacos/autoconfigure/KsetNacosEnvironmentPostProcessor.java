package com.kset.nacos.autoconfigure;

import com.kset.cloud.config.KsetCloudProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 补全 Nacos / KSet 云服务默认配置（仅由 starter-nacos 注册，避免单机 mysql/redis 误带入）。
 */
public class KsetNacosEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String PROPERTY_SOURCE_NAME = "ksetNacosDefaults";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        KsetCloudProperties properties = new KsetCloudProperties();

        Map<String, Object> defaults = new LinkedHashMap<>();
        putIfMissing(environment, defaults, "spring.cloud.nacos.discovery.namespace",
                properties.getNacos().getNamespace());
        putIfMissing(environment, defaults, "spring.cloud.nacos.config.namespace",
                properties.getNacos().getNamespace());
        putIfMissing(environment, defaults, "spring.cloud.nacos.discovery.group",
                properties.getNacos().getGroup());
        putIfMissing(environment, defaults, "spring.cloud.nacos.config.group",
                properties.getNacos().getGroup());

        appendConfigImport(environment, defaults,
                "optional:nacos:" + properties.getNacos().getCommonConfigDataId());

        if (!defaults.isEmpty()) {
            environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, defaults));
        }
    }

    private void putIfMissing(ConfigurableEnvironment environment, Map<String, Object> defaults,
                              String key, Object value) {
        if (!environment.containsProperty(key) && value != null) {
            defaults.put(key, value);
        }
    }

    private void appendConfigImport(ConfigurableEnvironment environment, Map<String, Object> defaults,
                                    String importEntry) {
        String existing = environment.getProperty("spring.config.import");
        if (existing == null || existing.isBlank()) {
            defaults.put("spring.config.import", importEntry);
            return;
        }
        if (!existing.contains(importEntry)) {
            defaults.put("spring.config.import", existing + "," + importEntry);
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
