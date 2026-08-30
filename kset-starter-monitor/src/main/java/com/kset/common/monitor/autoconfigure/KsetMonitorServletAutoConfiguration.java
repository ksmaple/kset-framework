package com.kset.common.monitor.autoconfigure;

import com.kset.common.monitor.config.KsetMonitorCloudCompat;
import com.kset.common.monitor.config.KsetMonitorProperties;
import com.kset.common.monitor.interceptor.MvcMonitorInterceptor;
import com.kset.common.monitor.web.GrayTagServletFilter;
import com.kset.common.monitor.web.TraceIdFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = "jakarta.servlet.Filter")
@ConditionalOnProperty(prefix = "kset.monitor", name = "enabled", havingValue = "true", matchIfMissing = true)
public class KsetMonitorServletAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "kset.monitor.servlet", name = "trace-enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean({TraceIdFilter.class, MvcMonitorInterceptor.class})
    public TraceIdFilter traceIdFilter() {
        return new TraceIdFilter();
    }

    @Bean
    @ConditionalOnProperty(prefix = "kset.monitor.servlet", name = "trace-enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnBean(TraceIdFilter.class)
    @ConditionalOnMissingBean(name = "traceIdFilterRegistration")
    public FilterRegistrationBean<TraceIdFilter> traceIdFilterRegistration(TraceIdFilter traceIdFilter) {
        FilterRegistrationBean<TraceIdFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(traceIdFilter);
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.setName("ksetTraceIdFilter");
        return registration;
    }

    @Bean
    @ConditionalOnProperty(prefix = "kset.monitor.servlet", name = "gray-tag-enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public GrayTagServletFilter grayTagServletFilter(KsetMonitorProperties properties, Environment environment) {
        return new GrayTagServletFilter(KsetMonitorCloudCompat.defaultGrayTag(properties, environment));
    }

    @Bean
    @ConditionalOnProperty(prefix = "kset.monitor.servlet", name = "gray-tag-enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(name = "grayTagServletFilterRegistration")
    public FilterRegistrationBean<GrayTagServletFilter> grayTagServletFilterRegistration(
            GrayTagServletFilter grayTagServletFilter) {
        FilterRegistrationBean<GrayTagServletFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(grayTagServletFilter);
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 5);
        registration.setName("ksetGrayTagServletFilter");
        return registration;
    }
}
