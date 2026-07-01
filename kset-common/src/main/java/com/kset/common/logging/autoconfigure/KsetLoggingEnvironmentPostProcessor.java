package com.kset.common.logging.autoconfigure;

import com.kset.common.logging.config.KsetLoggingProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

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
    static final String BUSINESS_DEBUG_ENABLED_KEY = "kset.logging.business-debug.enabled";
    static final String BUSINESS_DEBUG_PACKAGES_KEY = "kset.logging.business-debug.packages";
    static final String LOGGING_CONFIG_KEY = "logging.config";
    static final String LOGGING_LEVEL_PREFIX = "logging.level.";
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
        String defaultProfile = resolveDefaultProfile(environment);
        if (!environment.containsProperty(PROFILES_DEFAULT_KEY)
                && !environment.containsProperty(PROFILES_ACTIVE_KEY)) {
            defaults.put(PROFILES_DEFAULT_KEY, defaultProfile);
        }
        addBusinessDebugDefaults(environment, defaults, defaultProfile);
        environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, defaults));
    }

    private static String resolveDefaultProfile(ConfigurableEnvironment environment) {
        return environment.getProperty(
                DEFAULT_PROFILE_KEY,
                KsetLoggingProperties.DEFAULT_PROFILE);
    }

    private static void addBusinessDebugDefaults(
            ConfigurableEnvironment environment,
            Map<String, Object> defaults,
            String defaultProfile) {
        boolean enabled = environment.getProperty(
                BUSINESS_DEBUG_ENABLED_KEY,
                Boolean.class,
                isDevProfile(environment, defaultProfile));
        defaults.put(BUSINESS_DEBUG_ENABLED_KEY, enabled);
        if (!enabled) {
            return;
        }
        String packages = environment.getProperty(BUSINESS_DEBUG_PACKAGES_KEY);
        if (!StringUtils.hasText(packages)) {
            return;
        }
        for (String packageName : StringUtils.commaDelimitedListToStringArray(packages)) {
            String trimmedPackageName = packageName.trim();
            if (!StringUtils.hasText(trimmedPackageName)) {
                continue;
            }
            String levelKey = LOGGING_LEVEL_PREFIX + trimmedPackageName;
            if (!environment.containsProperty(levelKey)) {
                defaults.put(levelKey, "DEBUG");
            }
        }
    }

    private static boolean isDevProfile(ConfigurableEnvironment environment, String defaultProfile) {
        String activeProfiles = environment.getProperty(PROFILES_ACTIVE_KEY);
        if (StringUtils.hasText(activeProfiles)) {
            return containsProfile(activeProfiles, "dev");
        }
        String configuredDefaultProfiles = environment.getProperty(PROFILES_DEFAULT_KEY);
        if (StringUtils.hasText(configuredDefaultProfiles)) {
            return containsProfile(configuredDefaultProfiles, "dev")
                    || containsProfile(configuredDefaultProfiles, "default");
        }
        return containsProfile(defaultProfile, "dev")
                || containsProfile(defaultProfile, "default");
    }

    private static boolean containsProfile(String profiles, String expectedProfile) {
        for (String profile : StringUtils.commaDelimitedListToStringArray(profiles)) {
            if (expectedProfile.equals(profile.trim())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }
}
