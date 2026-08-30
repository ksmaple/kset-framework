package com.kset.common.monitor.autoconfigure;

import com.kset.common.monitor.config.KsetMonitorCloudCompat;
import com.kset.common.monitor.config.KsetMonitorProperties;
import com.kset.common.monitor.gateway.TraceIdGatewayFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

@AutoConfiguration
@ConditionalOnClass(name = "org.springframework.cloud.gateway.filter.GatewayFilterChain")
@ConditionalOnProperty(prefix = "kset.monitor", name = "enabled", havingValue = "true", matchIfMissing = true)
public class KsetMonitorGatewayAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "traceIdGatewayFilter")
    @ConditionalOnProperty(prefix = "kset.monitor.gateway", name = {"enabled", "trace-enabled"}, havingValue = "true", matchIfMissing = true)
    public GlobalFilter traceIdGatewayFilter(KsetMonitorProperties properties, Environment environment) {
        return new TraceIdGatewayFilter(KsetMonitorCloudCompat.gatewayTraceHeader(properties, environment));
    }
}
