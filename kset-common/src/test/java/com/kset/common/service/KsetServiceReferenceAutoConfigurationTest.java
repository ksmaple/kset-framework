package com.kset.common.service;

import com.kset.common.service.autoconfigure.KsetServiceReferenceAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class KsetServiceReferenceAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(KsetServiceReferenceAutoConfiguration.class));

    @Test
    void injectsLocalServiceWithoutDubboStarter() {
        GreetingService local = name -> "local " + name;

        contextRunner.withBean("localGreetingService", GreetingService.class, () -> local)
                .withBean(Consumer.class, Consumer::new)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(Consumer.class).service).isSameAs(local);
                });
    }

    private interface GreetingService {
        String greet(String name);
    }

    private static class Consumer {
        @KsetReference
        private GreetingService service;
    }
}
