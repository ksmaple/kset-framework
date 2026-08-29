package com.kset.common.lifecycle.autoconfigure;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 补全优雅停机默认值：{@code server.shutdown=graceful} 与每相位停机超时。
 *
 * <p>仅在业务侧未显式配置时以最低优先级注入，用户配置始终优先。</p>
 */
public class KsetLifecycleEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String PROPERTY_SOURCE_NAME = "ksetLifecycleDefaults";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> defaults = new LinkedHashMap<>();
        putIfMissing(environment, defaults, "server.shutdown", "graceful");
        putIfMissing(environment, defaults, "spring.lifecycle.timeout-per-shutdown-phase", "30s");

        if (!defaults.isEmpty()) {
            environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, defaults));
        }
    }

    private static void putIfMissing(ConfigurableEnvironment environment, Map<String, Object> defaults,
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
