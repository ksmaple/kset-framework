package com.kset.dubbo.autoconfigure;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 补全 Dubbo 基础默认值。
 *
 * <p>目标是让业务侧只保留应用名与 Nacos 地址，其余 Dubbo 常用项由 starter 自动装配。</p>
 */
public class KsetDubboEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String PROPERTY_SOURCE_NAME = "ksetDubboDefaults";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> defaults = new LinkedHashMap<>();
        String appName = environment.getProperty("spring.application.name", "application");
        putIfMissing(environment, defaults, "dubbo.application.name", appName);
        putIfMissing(environment, defaults, "dubbo.protocol.name", "dubbo");
        putIfMissing(environment, defaults, "dubbo.protocol.port", "-1");
        putIfMissing(environment, defaults, "dubbo.consumer.check", "false");
        putIfMissing(environment, defaults, "dubbo.registry.register-mode", "instance");
        putIfMissing(environment, defaults, "dubbo.application.shutwait", "10000");

        String nacosServerAddr = firstNonBlank(
                environment.getProperty("spring.cloud.nacos.discovery.server-addr"),
                environment.getProperty("spring.cloud.nacos.config.server-addr"));
        if (nacosServerAddr != null) {
            putIfMissing(environment, defaults, "dubbo.registry.address", "nacos://" + nacosServerAddr);
        }

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

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        if (second != null && !second.isBlank()) {
            return second.trim();
        }
        return null;
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
