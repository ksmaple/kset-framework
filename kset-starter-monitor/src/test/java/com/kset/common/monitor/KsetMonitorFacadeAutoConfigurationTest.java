package com.kset.common.monitor;

import com.kset.common.monitor.autoconfigure.KsetMonitorFacadeAutoConfiguration;
import com.kset.common.monitor.config.KsetMonitorProperties;
import com.kset.common.monitor.facade.MonitorFacade;
import com.kset.common.monitor.reporter.AsyncReporter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class KsetMonitorFacadeAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(KsetMonitorProperties.class)
            .withConfiguration(AutoConfigurations.of(KsetMonitorFacadeAutoConfiguration.class));

    @Test
    void installsSynchronousFacadeWithoutAsyncReporter() {
        contextRunner.run(context -> assertThat(context)
                .hasSingleBean(MonitorFacade.class)
                .doesNotHaveBean(AsyncReporter.class));
    }

    @Test
    void legacyAsyncReporterPropertiesDoNotCreateAsyncReporter() {
        contextRunner
                .withPropertyValues(
                        "kset.monitor.reporter.async-enabled=true",
                        "kset.monitor.reporter.queue-capacity=16")
                .run(context -> assertThat(context)
                        .hasSingleBean(MonitorFacade.class)
                        .doesNotHaveBean(AsyncReporter.class));
    }
}
