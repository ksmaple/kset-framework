package com.kset.nacos.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KsetNacosEnvironmentPostProcessorTest {

    private final KsetNacosEnvironmentPostProcessor processor = new KsetNacosEnvironmentPostProcessor();

    @Test
    void doesNotAddConfigImportAfterConfigDataProcessing() {
        StandardEnvironment environment = new StandardEnvironment();

        processor.postProcessEnvironment(environment, new SpringApplication(Object.class));

        assertThat(environment.getProperty("spring.config.import")).isNull();
    }

    @Test
    void keepsExistingConfigImport() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "spring.config.import", "optional:file:../env/profiles/user-service.yml")));

        processor.postProcessEnvironment(environment, new SpringApplication(Object.class));

        assertThat(environment.getProperty("spring.config.import"))
                .isEqualTo("optional:file:../env/profiles/user-service.yml");
    }

    @Test
    void usesConfiguredKsetNacosDefaults() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "kset.cloud.nacos.namespace", "custom-ns",
                "kset.cloud.nacos.group", "CUSTOM_GROUP")));

        processor.postProcessEnvironment(environment, new SpringApplication(Object.class));

        assertThat(environment.getProperty("spring.cloud.nacos.config.namespace")).isEqualTo("custom-ns");
        assertThat(environment.getProperty("spring.cloud.nacos.discovery.namespace")).isEqualTo("custom-ns");
        assertThat(environment.getProperty("spring.cloud.nacos.config.group")).isEqualTo("CUSTOM_GROUP");
        assertThat(environment.getProperty("spring.cloud.nacos.discovery.group")).isEqualTo("CUSTOM_GROUP");
    }
}
