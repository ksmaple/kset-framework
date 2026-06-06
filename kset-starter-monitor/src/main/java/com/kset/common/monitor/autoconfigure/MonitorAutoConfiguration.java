package com.kset.common.monitor.autoconfigure;

import com.kset.common.monitor.aop.MonitorAspect;
import com.kset.common.monitor.aop.ScheduledMonitorAspect;
import com.kset.common.monitor.interceptor.MvcMonitorInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@AutoConfiguration
@ConditionalOnProperty(prefix = "kset.monitor", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MonitorAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "kset.monitor.aop", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnClass(name = "org.aspectj.lang.annotation.Aspect")
    public MonitorAspect monitorAspect() {
        return new MonitorAspect();
    }

    @Bean
    @ConditionalOnProperty(prefix = "kset.monitor.scheduled", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnClass(name = {
            "org.aspectj.lang.annotation.Aspect",
            "org.springframework.scheduling.annotation.Scheduled"
    })
    public ScheduledMonitorAspect scheduledMonitorAspect() {
        return new ScheduledMonitorAspect();
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(name = "org.springframework.web.servlet.HandlerInterceptor")
    @ConditionalOnProperty(prefix = "kset.monitor.web", name = "enabled", havingValue = "true", matchIfMissing = true)
    public MvcMonitorInterceptor mvcMonitorInterceptor() {
        return new MvcMonitorInterceptor();
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(name = "org.springframework.web.servlet.HandlerInterceptor")
    @ConditionalOnProperty(prefix = "kset.monitor.web", name = "enabled", havingValue = "true", matchIfMissing = true)
    public WebMvcConfigurer mvcMonitorConfigurer(MvcMonitorInterceptor mvcMonitorInterceptor) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(mvcMonitorInterceptor).addPathPatterns("/**").order(0);
            }
        };
    }
}
