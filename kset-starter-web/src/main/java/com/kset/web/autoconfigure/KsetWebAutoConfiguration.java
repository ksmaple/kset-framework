package com.kset.web.autoconfigure;

import com.kset.web.aop.OpLogAspect;
import com.kset.web.advice.TraceIdResponseBodyAdvice;
import com.kset.web.config.KsetWebMvcConfigurer;
import com.kset.web.config.KsetWebProperties;
import com.kset.web.filter.RequestLoggingFilter;
import com.kset.web.handler.GlobalExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(KsetWebProperties.class)
@Import({KsetWebMvcConfigurer.class, KsetKnife4jAutoConfiguration.class, TraceIdResponseBodyAdvice.class})
public class KsetWebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler globalExceptionHandler(KsetWebProperties properties) {
        return new GlobalExceptionHandler(properties);
    }

    @Bean
    @ConditionalOnClass(name = "org.aspectj.lang.annotation.Aspect")
    @ConditionalOnProperty(prefix = "kset.web.oplog", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public OpLogAspect opLogAspect(KsetWebProperties properties) {
        return new OpLogAspect(properties.getOplog().getUserIdHeader());
    }

    @Bean
    @ConditionalOnProperty(prefix = "kset.web.request-logging", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean
    public RequestLoggingFilter requestLoggingFilter() {
        return new RequestLoggingFilter();
    }

    @Bean
    @ConditionalOnProperty(prefix = "kset.web.request-logging", name = "enabled", havingValue = "true")
    public FilterRegistrationBean<RequestLoggingFilter> requestLoggingFilterRegistration(
            RequestLoggingFilter requestLoggingFilter) {
        FilterRegistrationBean<RequestLoggingFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(requestLoggingFilter);
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        registration.setName("requestLoggingFilter");
        return registration;
    }
}
