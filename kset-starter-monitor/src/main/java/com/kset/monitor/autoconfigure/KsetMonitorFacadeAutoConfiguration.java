package com.kset.monitor.autoconfigure;

import com.kset.common.monitor.KsetMonitor;
import com.kset.common.monitor.KsetMonitorFacade;
import com.kset.monitor.Monitor;
import com.kset.monitor.backend.MonitorBackend;
import com.kset.monitor.config.KsetMonitorProperties;
import com.kset.monitor.internal.DefaultMonitorFacade;
import com.kset.monitor.reporter.AsyncReporter;
import com.kset.monitor.reporter.DefaultAsyncReporter;
import com.kset.monitor.reporter.SyncAsyncReporter;
import com.kset.monitor.reporter.DefaultMetricAggregator;
import com.kset.monitor.reporter.MetricAggregator;
import com.kset.monitor.sampler.RateSampler;
import com.kset.monitor.sampler.Sampler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@ConditionalOnProperty(prefix = "kset.monitor", name = "enabled", havingValue = "true", matchIfMissing = true)
@Import(MonitorBackendConfiguration.class)
public class KsetMonitorFacadeAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public Sampler monitorSampler(KsetMonitorProperties properties) {
        return new RateSampler(properties.getSampler().getRate());
    }

    @Bean
    @ConditionalOnMissingBean
    public MetricAggregator monitorMetricAggregator() {
        return new DefaultMetricAggregator();
    }

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    public AsyncReporter monitorAsyncReporter(KsetMonitorProperties properties) {
        if (!properties.getReporter().isAsyncEnabled()) {
            return new SyncAsyncReporter();
        }
        return new DefaultAsyncReporter(properties.getReporter().getQueueCapacity());
    }

    @Bean
    @ConditionalOnMissingBean
    public KsetMonitorFacade ksetMonitorFacade(MonitorBackend monitorBackend,
                                               Sampler monitorSampler,
                                               AsyncReporter monitorAsyncReporter,
                                               MetricAggregator monitorMetricAggregator,
                                               KsetMonitorProperties properties) {
        return new DefaultMonitorFacade(
                monitorBackend,
                monitorSampler,
                monitorAsyncReporter,
                monitorMetricAggregator,
                properties.getReporter().isAsyncEnabled());
    }

    @Bean
    public Object ksetMonitorFacadeInstaller(KsetMonitorFacade facade) {
        Monitor.install(facade);
        KsetMonitor.install(facade);
        return new Object();
    }
}
