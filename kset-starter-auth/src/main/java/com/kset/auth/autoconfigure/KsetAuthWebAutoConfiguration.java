package com.kset.auth.autoconfigure;

import com.kset.auth.config.KsetAuthProperties;
import com.kset.auth.core.LoginAuthService;
import com.kset.auth.web.AuthExceptionHandler;
import com.kset.auth.web.DefaultServletAuthFailureHandler;
import com.kset.auth.web.LoginAuthFilter;
import com.kset.auth.web.ServletAuthFailureHandler;
import jakarta.servlet.Filter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.HandlerMapping;

import java.util.List;

@AutoConfiguration(after = KsetAuthAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "kset.auth", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnClass(Filter.class)
public class KsetAuthWebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "kset.auth.web", name = "enabled", havingValue = "true", matchIfMissing = true)
    public LoginAuthFilter loginAuthFilter(KsetAuthProperties properties,
                                           LoginAuthService authService,
                                           ServletAuthFailureHandler failureHandler,
                                           List<HandlerMapping> handlerMappings) {
        return new LoginAuthFilter(properties, authService, failureHandler, handlerMappings);
    }

    @Bean
    @ConditionalOnMissingBean
    public ServletAuthFailureHandler servletAuthFailureHandler() {
        return new DefaultServletAuthFailureHandler();
    }

    @Bean
    @ConditionalOnProperty(prefix = "kset.auth.web", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<LoginAuthFilter> loginAuthFilterRegistration(LoginAuthFilter loginAuthFilter) {
        FilterRegistrationBean<LoginAuthFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(loginAuthFilter);
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 5);
        registration.setName("loginAuthFilter");
        return registration;
    }

    @Bean
    @ConditionalOnMissingBean
    public AuthExceptionHandler authExceptionHandler() {
        return new AuthExceptionHandler();
    }
}
