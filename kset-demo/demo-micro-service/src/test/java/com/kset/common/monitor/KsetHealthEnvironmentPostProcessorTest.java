package com.kset.common.monitor;

import com.kset.common.monitor.autoconfigure.KsetHealthEnvironmentPostProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class KsetHealthEnvironmentPostProcessorTest {

    private final KsetHealthEnvironmentPostProcessor processor = new KsetHealthEnvironmentPostProcessor();

    @Test
    void appliesHealthDefaultsWhenMissing() {
        MockEnvironment environment = new MockEnvironment();

        processor.postProcessEnvironment(environment, new SpringApplication(Object.class));

        assertThat(environment.getProperty("management.endpoints.web.exposure.include"))
                .isEqualTo("health");
        assertThat(environment.getProperty("management.endpoint.health.probes.enabled", Boolean.class))
                .isTrue();
        assertThat(environment.getProperty("management.endpoint.health.show-details"))
                .isEqualTo("never");
        assertThat(environment.getProperty("management.health.livenessstate.enabled", Boolean.class))
                .isTrue();
        assertThat(environment.getProperty("management.health.readinessstate.enabled", Boolean.class))
                .isTrue();
    }

    @Test
    void respectsExistingHealthProperties() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("management.endpoints.web.exposure.include", "health,prometheus");
        environment.setProperty("management.endpoint.health.show-details", "when_authorized");

        processor.postProcessEnvironment(environment, new SpringApplication(Object.class));

        assertThat(environment.getProperty("management.endpoints.web.exposure.include"))
                .isEqualTo("health,prometheus");
        assertThat(environment.getProperty("management.endpoint.health.show-details"))
                .isEqualTo("when_authorized");
        assertThat(environment.getProperty("management.endpoint.health.probes.enabled", Boolean.class))
                .isTrue();
    }
}
