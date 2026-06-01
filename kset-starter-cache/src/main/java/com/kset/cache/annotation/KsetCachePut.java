package com.kset.cache.annotation;

import com.kset.cache.core.KsetCacheLayer;
import org.springframework.core.annotation.AliasFor;

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
     * 缓存 key，支持 SpEL 表达式；留空时按 Spring SimpleKey 规则生成。
     */
    String key() default "";

    /**
     * 自定义 key 生成器 Bean 名称；key 非空时优先使用 key 表达式。
     */
    String keyGenerator() default "";

    /**
     * 执行缓存操作前的条件表达式，返回 true 时才写入缓存。
     */
    String condition() default "";

    /**
     * 方法执行后的排除表达式，返回 true 时不写入缓存。
     */
    String unless() default "";

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
