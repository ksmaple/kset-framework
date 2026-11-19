package com.kset.nacos.autoconfigure;

import com.kset.cloud.config.KsetCloudProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
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
        KsetCloudProperties.Nacos nacos = Binder.get(environment)
                .bind("kset.cloud.nacos", Bindable.of(KsetCloudProperties.Nacos.class))
                .orElseGet(KsetCloudProperties.Nacos::new);

        Map<String, Object> defaults = new LinkedHashMap<>();
        putIfMissing(environment, defaults, "spring.cloud.nacos.discovery.namespace",
                nacos.getNamespace());
        putIfMissing(environment, defaults, "spring.cloud.nacos.config.namespace",
                nacos.getNamespace());
        putIfMissing(environment, defaults, "spring.cloud.nacos.discovery.group",
                nacos.getGroup());
        putIfMissing(environment, defaults, "spring.cloud.nacos.config.group",
                nacos.getGroup());

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

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
