package com.kset.auth.autoconfigure;

import com.kset.auth.aop.LoginAuthAspect;
import com.kset.auth.core.DefaultPermissionChecker;
import com.kset.auth.spi.PermissionChecker;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = KsetAuthAutoConfiguration.class)
@ConditionalOnClass(Aspect.class)
@ConditionalOnProperty(prefix = "kset.auth", name = "enabled", havingValue = "true", matchIfMissing = true)
public class KsetAuthAopAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public PermissionChecker permissionChecker() {
        return new DefaultPermissionChecker();
    }

    @Bean
    @ConditionalOnMissingBean
    public LoginAuthAspect loginAuthAspect(PermissionChecker permissionChecker) {
        return new LoginAuthAspect(permissionChecker);
    }
}
