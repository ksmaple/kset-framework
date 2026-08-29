package com.kset.dubbo.autoconfigure;

import com.kset.dubbo.lifecycle.KsetDubboLifecycle;
import com.kset.dubbo.route.DubboRouteRuleProvider;
import org.apache.dubbo.config.bootstrap.DubboBootstrap;
import org.apache.dubbo.rpc.Filter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(Filter.class)
public class KsetDubboGovernanceAutoConfiguration {

    @Bean
    public DubboRouteRuleProvider dubboRouteRuleProvider() {
        return new DubboRouteRuleProvider();
    }

    @Bean
    @ConditionalOnClass(DubboBootstrap.class)
    @ConditionalOnProperty(prefix = "kset.lifecycle", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public KsetDubboLifecycle ksetDubboLifecycle() {
        return new KsetDubboLifecycle();
    }
}
