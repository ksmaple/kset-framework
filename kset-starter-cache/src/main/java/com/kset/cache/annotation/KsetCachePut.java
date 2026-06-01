package com.kset.cache.annotation;

import com.kset.cache.core.KsetCacheLayer;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
/**
 * 方法执行后强制写入缓存，适合更新后刷新指定 key。
 */
public @interface KsetCachePut {

    /**
     * 缓存名称，用于区分业务缓存空间。
     */
    String cacheName();

    /**
     * 缓存 key，支持 SpEL 表达式。
     */
    String key();

    /**
     * 写入的缓存层级。
     */
    KsetCacheLayer[] layers() default {};

    /**
     * 非空结果的过期时间；空值时使用全局或层级默认 TTL。
     */
    String ttl() default "";

    /**
     * 空值结果的过期时间。
     */
    String nullTtl() default "";

    /**
     * 是否允许把 null 结果写入缓存。
     */
    boolean cacheNull() default true;
}
