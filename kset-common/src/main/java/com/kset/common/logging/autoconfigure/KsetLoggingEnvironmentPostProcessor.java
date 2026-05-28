package com.kset.common.logging.autoconfigure;

import com.kset.common.logging.config.KsetLoggingProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 在未显式指定 {@code logging.config} 时，启用 KSet 统一 Logback 配置。
 *
 * <p>配置文件位于 {@link KsetLoggingProperties#DEFAULT_CONFIG_LOCATION}（kset-common 资源），
 * 接入方依赖任意 KSet Starter 即可继承，无需再复制 logback 配置。</p>
 */
public class KsetLoggingEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String PROPERTY_SOURCE_NAME = "ksetLoggingDefaults";
    static final String AUTO_CONFIG_KEY = "kset.logging.auto-config";
    static final String DEFAULT_PROFILE_KEY = "kset.logging.default-profile";
    static final String LOGGING_CONFIG_KEY = "logging.config";
    static final String PROFILES_DEFAULT_KEY = "spring.profiles.default";
    static final String PROFILES_ACTIVE_KEY = "spring.profiles.active";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!environment.getProperty(AUTO_CONFIG_KEY, Boolean.class, Boolean.TRUE)) {
            return;
        }
        if (environment.containsProperty(LOGGING_CONFIG_KEY)) {
            return;
        }
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put(LOGGING_CONFIG_KEY, KsetLoggingProperties.DEFAULT_CONFIG_LOCATION);
        if (!environment.containsProperty(PROFILES_DEFAULT_KEY)
                && !environment.containsProperty(PROFILES_ACTIVE_KEY)) {
            defaults.put(PROFILES_DEFAULT_KEY, resolveDefaultProfile(environment));
        }
        environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, defaults));
    }

    private static String resolveDefaultProfile(ConfigurableEnvironment environment) {
        return environment.getProperty(
                DEFAULT_PROFILE_KEY,
                KsetLoggingProperties.DEFAULT_PROFILE);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }
}
