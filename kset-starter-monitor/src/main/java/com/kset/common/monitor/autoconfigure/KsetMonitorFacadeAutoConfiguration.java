package com.kset.common.monitor.autoconfigure;

import com.kset.common.monitor.Monitor;
import com.kset.common.monitor.backend.MonitorBackend;
import com.kset.common.monitor.config.KsetMonitorProperties;
import com.kset.common.monitor.facade.MonitorFacade;
import com.kset.common.monitor.internal.DefaultMonitorFacade;
import com.kset.common.monitor.lifecycle.KsetMonitorLifecycle;
import com.kset.common.monitor.reporter.AsyncReporter;
import com.kset.common.monitor.reporter.DefaultMetricAggregator;
import com.kset.common.monitor.reporter.MetricAggregator;
import com.kset.common.monitor.sampler.RateSampler;
import com.kset.common.monitor.sampler.Sampler;
import org.springframework.beans.factory.ObjectProvider;
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

    @Bean
    @ConditionalOnMissingBean
    public MonitorFacade ksetMonitorFacade(MonitorBackend monitorBackend,
                                           Sampler monitorSampler,
                                           MetricAggregator monitorMetricAggregator,
                                           KsetMonitorProperties properties) {
        return new DefaultMonitorFacade(
                monitorBackend,
                monitorSampler,
                monitorMetricAggregator);
    }

    @Bean
    @ConditionalOnProperty(prefix = "kset.lifecycle", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public KsetMonitorLifecycle ksetMonitorLifecycle(ObjectProvider<MetricAggregator> metricAggregators,
                                                     ObjectProvider<AsyncReporter> asyncReporters) {
        return new KsetMonitorLifecycle(metricAggregators, asyncReporters);
    }

    @Bean
    public Object ksetMonitorFacadeInstaller(MonitorFacade facade) {
        Monitor.install(facade);
        return new Object();
    }
}
