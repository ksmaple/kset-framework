package com.kset.redis.autoconfigure;

import com.kset.redis.config.KsetRedisProperties;
import com.kset.redis.lock.KsetRedisLockExecutor;
import com.kset.redis.lock.aop.KsetRedisLockAnnotationValidator;
import com.kset.redis.lock.aop.KsetRedisLockAspect;
import com.kset.redis.lock.internal.KsetRedissonLockProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * {@link com.kset.redis.lock.annotation.KsetLocked} 注解锁自动配置。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = "org.aspectj.lang.annotation.Aspect")
@ConditionalOnBean({KsetRedisLockExecutor.class, KsetRedissonLockProvider.class})
@ConditionalOnProperty(prefix = "kset.redis.lock", name = "annotation-enabled", havingValue = "true", matchIfMissing = true)
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class KsetRedisLockAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public KsetRedisLockAspect ksetRedisLockAspect(KsetRedisLockExecutor executor,
                                                   KsetRedissonLockProvider lockProvider) {
        return new KsetRedisLockAspect(executor, lockProvider);
    }

    @Bean
    @ConditionalOnMissingBean
    public KsetRedisLockAnnotationValidator ksetRedisLockAnnotationValidator(ApplicationContext applicationContext,
                                                                             KsetRedisProperties properties) {
        return new KsetRedisLockAnnotationValidator(applicationContext,
                properties.getLock().isValidateTargets());
    }
}
