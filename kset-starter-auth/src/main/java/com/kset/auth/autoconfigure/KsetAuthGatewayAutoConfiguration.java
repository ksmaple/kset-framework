package com.kset.auth.autoconfigure;

import com.kset.auth.config.KsetAuthProperties;
import com.kset.auth.core.LoginAuthService;
import com.kset.auth.gateway.DefaultGatewayAuthFailureHandler;
import com.kset.auth.gateway.GatewayAuthFailureHandler;
import com.kset.auth.gateway.LoginAuthGatewayFilter;
import com.kset.auth.spi.LoginUserHeaderCodec;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = KsetAuthAutoConfiguration.class)
@ConditionalOnClass(GlobalFilter.class)
@ConditionalOnProperty(prefix = "kset.auth", name = "enabled", havingValue = "true", matchIfMissing = true)
public class KsetAuthGatewayAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "loginAuthGatewayFilter")
    @ConditionalOnProperty(prefix = "kset.auth.gateway", name = "enabled", havingValue = "true", matchIfMissing = true)
    public GlobalFilter loginAuthGatewayFilter(KsetAuthProperties properties,
                                               LoginAuthService authService,
                                               LoginUserHeaderCodec headerCodec,
                                               GatewayAuthFailureHandler failureHandler) {
        return new LoginAuthGatewayFilter(properties, authService, headerCodec, failureHandler);
    }

    @Bean
    @ConditionalOnMissingBean
    public GatewayAuthFailureHandler gatewayAuthFailureHandler() {
        return new DefaultGatewayAuthFailureHandler();
    }
}
