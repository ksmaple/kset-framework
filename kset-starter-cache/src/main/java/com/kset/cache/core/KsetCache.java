package com.kset.cache.core;

import java.util.Optional;
import java.util.concurrent.Callable;

public final class KsetCache {

    private static volatile KsetCacheFacade facade = new NoOpKsetCacheFacade();

    private KsetCache() {
    }

    public static void bind(KsetCacheFacade cacheFacade) {
        if (cacheFacade != null) {
            facade = cacheFacade;
        }
    }

    public static KsetCacheFacade facade() {
        return facade;
    }

    public static <T> Optional<T> get(String cacheName, String key, Class<T> type) {
        return facade.getValue(KsetCacheSpec.builder(cacheName, key).valueType(type).build(), type);
    }

    public static <T> Optional<T> get(KsetCacheSpec spec, Class<T> type) {
        return facade.getValue(spec, type);
    }

    public static void put(String cacheName, String key, Object value) {
        facade.put(KsetCacheSpec.builder(cacheName, key).build(), value);
    }

    public static void put(KsetCacheSpec spec, Object value) {
        facade.put(spec, value);
    }

    public static void evict(String cacheName, String key) {
        facade.evict(KsetCacheSpec.builder(cacheName, key).build());
    }

    public static void evict(KsetCacheSpec spec) {
        facade.evict(spec);
    }

    public static void clear(String cacheName) {
        facade.clear(cacheName);
    }

    public static void clear(String cacheName, java.util.List<KsetCacheLayer> layers) {
        facade.clear(cacheName, layers);
    }

    public static <T> T getOrLoad(String cacheName, String key, Class<T> type, Callable<T> loader) throws Exception {
        return facade.getOrLoadValue(KsetCacheSpec.builder(cacheName, key).valueType(type).build(), type, loader);
    }

    public static <T> T getOrLoad(KsetCacheSpec spec, Class<T> type, Callable<T> loader) throws Exception {
        return facade.getOrLoadValue(spec, type, loader);
    }

    public static KsetCacheMetrics metrics() {
        return facade.metrics();
    }
}
