package com.kset.redis.autoconfigure;

import com.kset.redis.web.KsetRedisLockExceptionHandler;
import com.kset.web.config.KsetWebProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Maps Redis lock exceptions to {@code ApiResponse} when Web is on the classpath.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = {
        "org.springframework.web.bind.annotation.RestControllerAdvice",
        "com.kset.web.response.ApiResponse",
        "com.kset.web.config.KsetWebProperties"
})
public class KsetRedisLockWebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public KsetRedisLockExceptionHandler ksetRedisLockExceptionHandler(
            ObjectProvider<KsetWebProperties> webProperties) {
        return new KsetRedisLockExceptionHandler(webProperties.getIfAvailable());
    }
}
