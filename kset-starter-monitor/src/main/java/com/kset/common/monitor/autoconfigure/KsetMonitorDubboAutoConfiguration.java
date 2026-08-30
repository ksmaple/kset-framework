package com.kset.common.monitor.autoconfigure;

import com.kset.common.monitor.config.KsetMonitorCloudCompat;
import com.kset.common.monitor.config.KsetMonitorProperties;
import com.kset.common.monitor.dubbo.DubboTraceFilter;
import org.apache.dubbo.rpc.Filter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

@AutoConfiguration
@ConditionalOnClass(Filter.class)
@ConditionalOnProperty(prefix = "kset.monitor", name = "enabled", havingValue = "true", matchIfMissing = true)
public class KsetMonitorDubboAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "dubboTraceFilter")
    @ConditionalOnProperty(prefix = "kset.monitor.dubbo", name = "enabled", havingValue = "true", matchIfMissing = true)
    public Filter dubboTraceFilter(KsetMonitorProperties properties, Environment environment) {
        return new DubboTraceFilter(
                KsetMonitorCloudCompat.tracePropagationEnabled(properties, environment),
                KsetMonitorCloudCompat.defaultGrayTag(properties, environment));
    }
}
