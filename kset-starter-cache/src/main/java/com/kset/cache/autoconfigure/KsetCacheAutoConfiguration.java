package com.kset.cache.autoconfigure;

import com.kset.cache.config.KsetCacheProperties;
import com.kset.cache.core.DefaultKsetCacheFacade;
import com.kset.cache.core.KsetCacheBootstrap;
import com.kset.cache.core.KsetCacheFacade;
import com.kset.cache.core.KsetCacheStore;
import com.kset.cache.interceptor.KsetCacheAspect;
import com.kset.cache.interceptor.KsetCacheKeyEvaluator;
import com.kset.cache.store.CaffeineKsetCacheStore;
import com.kset.cache.spring.KsetSpringCacheManager;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;

import java.util.List;

@AutoConfiguration
@EnableConfigurationProperties(KsetCacheProperties.class)
@ConditionalOnProperty(prefix = "kset.cache", name = "enabled", havingValue = "true", matchIfMissing = true)
public class KsetCacheAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "ksetCaffeineCacheStore")
    @ConditionalOnProperty(prefix = "kset.cache.l1", name = "enabled", havingValue = "true", matchIfMissing = true)
    public KsetCacheStore ksetCaffeineCacheStore(KsetCacheProperties properties) {
        return new CaffeineKsetCacheStore(properties.getL1().getMaximumSize());
    }

    @Bean
    @ConditionalOnMissingBean
    public KsetCacheFacade ksetCacheFacade(List<KsetCacheStore> stores, KsetCacheProperties properties) {
        return new DefaultKsetCacheFacade(stores, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public KsetCacheKeyEvaluator ksetCacheKeyEvaluator() {
        return new KsetCacheKeyEvaluator();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(KsetCacheAspect.class)
    public KsetCacheAspect ksetCacheAspect(KsetCacheFacade cacheFacade,
                                           KsetCacheProperties properties,
                                           KsetCacheKeyEvaluator keyEvaluator) {
        return new KsetCacheAspect(cacheFacade, properties, keyEvaluator);
    }

    @Bean
    @ConditionalOnMissingBean
    public KsetCacheBootstrap ksetCacheBootstrap(KsetCacheFacade cacheFacade) {
        return new KsetCacheBootstrap(cacheFacade);
    }

    @Bean
    @ConditionalOnMissingBean(CacheManager.class)
    @ConditionalOnProperty(prefix = "kset.cache.spring", name = "enabled", havingValue = "true", matchIfMissing = true)
    public CacheManager ksetSpringCacheManager(KsetCacheFacade cacheFacade, KsetCacheProperties properties) {
        return new KsetSpringCacheManager(cacheFacade, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public KsetCacheConfigurationValidator ksetCacheConfigurationValidator(List<KsetCacheStore> stores,
                                                                            KsetCacheProperties properties) {
        return new KsetCacheConfigurationValidator(stores, properties);
    }
}
