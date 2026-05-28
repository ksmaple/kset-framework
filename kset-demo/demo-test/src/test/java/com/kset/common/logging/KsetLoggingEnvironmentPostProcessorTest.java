package com.kset.common.logging;

import com.kset.common.logging.autoconfigure.KsetLoggingEnvironmentPostProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class KsetLoggingEnvironmentPostProcessorTest {

    private final KsetLoggingEnvironmentPostProcessor processor = new KsetLoggingEnvironmentPostProcessor();

    @Test
    void appliesDefaultLoggingConfigWhenMissing() {
        MockEnvironment environment = new MockEnvironment();
        processor.postProcessEnvironment(environment, new SpringApplication(Object.class));
        assertThat(environment.getProperty("logging.config"))
                .isEqualTo("classpath:kset-logback-spring.xml");
        assertThat(environment.getProperty("spring.profiles.default")).isEqualTo("dev");
    }

    @Test
    void respectsExistingActiveProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("spring.profiles.active", "prod");
        processor.postProcessEnvironment(environment, new SpringApplication(Object.class));
        assertThat(environment.getProperty("spring.profiles.default")).isNull();
    }

    @Test
    void respectsExistingDefaultProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("spring.profiles.default", "test");
        processor.postProcessEnvironment(environment, new SpringApplication(Object.class));
        assertThat(environment.getProperty("spring.profiles.default")).isEqualTo("test");
    }

    @Test
    void respectsExistingLoggingConfig() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("logging.config", "classpath:custom-logback.xml");
        processor.postProcessEnvironment(environment, new SpringApplication(Object.class));
        assertThat(environment.getProperty("logging.config"))
                .isEqualTo("classpath:custom-logback.xml");
    }

    @Test
    void canDisableAutoConfig() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("kset.logging.auto-config", "false");
        processor.postProcessEnvironment(environment, new SpringApplication(Object.class));
        assertThat(environment.getProperty("logging.config")).isNull();
    }

    @Test
    void respectsCustomDefaultProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("kset.logging.default-profile", "test");
        processor.postProcessEnvironment(environment, new SpringApplication(Object.class));
        assertThat(environment.getProperty("spring.profiles.default")).isEqualTo("test");
    }
}
