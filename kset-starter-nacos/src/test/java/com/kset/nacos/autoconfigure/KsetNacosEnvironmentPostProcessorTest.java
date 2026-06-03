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
    void addsNacosImportWhenMissing() {
        StandardEnvironment environment = new StandardEnvironment();

        processor.postProcessEnvironment(environment, new SpringApplication(Object.class));

        assertThat(environment.getProperty("spring.config.import"))
                .isEqualTo("optional:nacos:kset-common.yaml");
    }

    @Test
    void mergesNacosImportWhenFileImportAlreadyPresent() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "spring.config.import", "optional:file:../env/profiles/user-service.yml")));

        processor.postProcessEnvironment(environment, new SpringApplication(Object.class));

        assertThat(environment.getProperty("spring.config.import"))
                .contains("optional:file:../env/profiles/user-service.yml")
                .contains("optional:nacos:kset-common.yaml");
    }
}
