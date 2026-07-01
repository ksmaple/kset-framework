package com.kset.common.logging.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KsetLoggingEnvironmentPostProcessorTest {

    private final KsetLoggingEnvironmentPostProcessor postProcessor = new KsetLoggingEnvironmentPostProcessor();

    @Test
    void defaultsToDevProfileAndBusinessDebugEnabled() {
        StandardEnvironment environment = new StandardEnvironment();

        postProcessor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("spring.profiles.default")).isEqualTo("dev");
        assertThat(environment.getProperty("kset.logging.business-debug.enabled", Boolean.class)).isTrue();
    }

    @Test
    void disablesBusinessDebugByDefaultForTestAndProd() {
        StandardEnvironment testEnvironment = environmentWith("spring.profiles.active", "test");
        StandardEnvironment prodEnvironment = environmentWith("spring.profiles.active", "prod");

        postProcessor.postProcessEnvironment(testEnvironment, null);
        postProcessor.postProcessEnvironment(prodEnvironment, null);

        assertThat(testEnvironment.getProperty("kset.logging.business-debug.enabled", Boolean.class)).isFalse();
        assertThat(prodEnvironment.getProperty("kset.logging.business-debug.enabled", Boolean.class)).isFalse();
    }

    @Test
    void injectsDebugLevelForConfiguredBusinessPackagesInDev() {
        StandardEnvironment environment = environmentWith(
                "kset.logging.business-debug.packages",
                "com.example.order, com.example.user");

        postProcessor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("logging.level.com.example.order")).isEqualTo("DEBUG");
        assertThat(environment.getProperty("logging.level.com.example.user")).isEqualTo("DEBUG");
    }

    @Test
    void doesNotOverrideExplicitBusinessPackageLevel() {
        StandardEnvironment environment = environmentWith(
                "kset.logging.business-debug.packages",
                "com.example.order",
                "logging.level.com.example.order",
                "INFO");

        postProcessor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("logging.level.com.example.order")).isEqualTo("INFO");
    }

    @Test
    void doesNotInjectDebugLevelWithoutBusinessPackages() {
        StandardEnvironment environment = new StandardEnvironment();

        postProcessor.postProcessEnvironment(environment, null);

        MapPropertySource defaults = (MapPropertySource) environment.getPropertySources()
                .get(KsetLoggingEnvironmentPostProcessor.PROPERTY_SOURCE_NAME);

        assertThat(defaults).isNotNull();
        assertThat(defaults.getSource().keySet()).noneMatch(key -> key.startsWith("logging.level."));
    }

    private static StandardEnvironment environmentWith(String key, Object value) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", Map.of(key, value)));
        return environment;
    }

    private static StandardEnvironment environmentWith(String firstKey, Object firstValue, String secondKey, Object secondValue) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource(
                "test",
                Map.of(firstKey, firstValue, secondKey, secondValue)));
        return environment;
    }
}
