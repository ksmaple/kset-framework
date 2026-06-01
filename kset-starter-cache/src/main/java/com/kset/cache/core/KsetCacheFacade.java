package com.kset.cache.core;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Function;

/**
 * KSet 缓存统一门面，屏蔽 L1/L2 存储与注解切面的调用差异。
 */
public interface KsetCacheFacade {

    /**
     * 按缓存规格读取原始缓存值。
     */
    Optional<KsetCacheValue> get(KsetCacheSpec spec);

    /**
     * 按缓存规格写入业务值，内部会包装 null 标记与写入时间。
     */
    void put(KsetCacheSpec spec, Object value);

    /**
     * 删除指定缓存规格对应的所有层级缓存。
     */
    void evict(KsetCacheSpec spec);

    /**
     * Clear all entries under the cache namespace for the configured default layers.
     */
    void clear(String cacheName);

    /**
     * Clear all entries under the cache namespace for the specified layers.
     */
    void clear(String cacheName, List<KsetCacheLayer> layers);

    /**
     * 多层缓存读取；全部未命中时调用 loader，并按规格回填缓存。
     */
    Object getOrLoad(List<KsetCacheSpec> specs, Callable<Object> loader) throws Exception;

    /**
     * 多层缓存读取；全部未命中时调用 loader，并由 selector 按加载结果决定需要回填的规格。
     */
    Object getOrLoad(List<KsetCacheSpec> specs,
                     Callable<Object> loader,
                     Function<Object, List<KsetCacheSpec>> writeSpecSelector) throws Exception;

    /**
     * 读取并转换为指定类型的业务值。
     */
    <T> Optional<T> getValue(KsetCacheSpec spec, Class<T> type);

    /**
     * 读取指定类型业务值；未命中时加载并回填。
     */
    <T> T getOrLoadValue(KsetCacheSpec spec, Class<T> type, Callable<T> loader) throws Exception;

    /**
     * 返回当前缓存命中、未命中和加载次数等统计快照。
     */
    KsetCacheMetrics metrics();
}
