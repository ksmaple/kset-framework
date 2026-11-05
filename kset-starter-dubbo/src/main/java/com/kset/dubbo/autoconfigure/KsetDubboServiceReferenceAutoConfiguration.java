package com.kset.dubbo.autoconfigure;

import com.kset.common.service.RemoteServiceReferenceProvider;
import com.kset.dubbo.reference.DubboServiceReferenceProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

@AutoConfiguration
public class KsetDubboServiceReferenceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(RemoteServiceReferenceProvider.class)
    public DubboServiceReferenceProvider dubboServiceReferenceProvider(Environment environment) {
        return new DubboServiceReferenceProvider(environment);
    }
}
