package com.kset.cache.annotation;

import com.kset.cache.core.KsetCacheLayer;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
/**
 * 类级缓存默认配置，对齐 Spring Cache 的 CacheConfig 迁移习惯。
 */
public @interface KsetCacheConfig {

    /**
     * 缓存名称，用于区分业务缓存空间。
     */
    String cacheName() default "";

    /**
     * Spring Cache 风格缓存名称别名。
     */
    @AliasFor("cacheNames")
    String[] value() default {};

    /**
     * Spring Cache 风格缓存名称别名。
     */
    @AliasFor("value")
    String[] cacheNames() default {};

    /**
     * 自定义 key 生成器 Bean 名称。
     */
    String keyGenerator() default "";

    /**
     * 类级默认缓存层级；未指定时使用 kset.cache.default-layers。
     */
    KsetCacheLayer[] layers() default {};

}
