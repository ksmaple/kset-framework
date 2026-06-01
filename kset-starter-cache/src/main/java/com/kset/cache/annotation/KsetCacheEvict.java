package com.kset.cache.annotation;

import com.kset.cache.core.KsetCacheLayer;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
/**
 * 删除指定缓存 key，适合写操作后清理旧值。
 */
public @interface KsetCacheEvict {

    /**
     * 缓存名称，用于区分业务缓存空间。
     */
    String cacheName();

    /**
     * 缓存 key，支持 SpEL 表达式。
     */
    String key();

    /**
     * 需要清理的缓存层级。
     */
    KsetCacheLayer[] layers() default {};

    /**
     * 是否在目标方法执行前清理缓存。
     */
    boolean beforeInvocation() default false;
}
