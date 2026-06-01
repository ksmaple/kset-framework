package com.kset.cache.interceptor;

import java.lang.reflect.Method;

/**
 * KSet 缓存 key 生成器；用于替代 Spring Cache 的 KeyGenerator 扩展点。
 */
public interface KsetCacheKeyGenerator {

    /**
     * 根据目标对象、方法和参数生成缓存 key。
     */
    String generate(Object target, Method method, Object[] args);
}
