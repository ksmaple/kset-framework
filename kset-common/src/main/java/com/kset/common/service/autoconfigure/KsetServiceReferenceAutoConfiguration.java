package com.kset.common.service.autoconfigure;

import com.kset.common.service.KsetServiceRefBeanPostProcessor;
import com.kset.common.service.KsetServiceRefConflictValidator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class KsetServiceReferenceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public static KsetServiceRefBeanPostProcessor ksetServiceRefBeanPostProcessor() {
        return new KsetServiceRefBeanPostProcessor();
    }

    @Bean
    @ConditionalOnMissingBean
    public KsetServiceRefConflictValidator ksetServiceRefConflictValidator(
            KsetServiceRefBeanPostProcessor processor) {
        return new KsetServiceRefConflictValidator(processor);
    }
}
