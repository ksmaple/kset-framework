package com.kset.cache.spring;

import com.kset.cache.config.KsetCacheProperties;
import com.kset.cache.core.KsetCacheFacade;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class KsetSpringCacheManager implements CacheManager {

    private final KsetCacheFacade cacheFacade;
    private final KsetCacheProperties properties;
    private final Map<String, Cache> caches = new ConcurrentHashMap<>();

    public KsetSpringCacheManager(KsetCacheFacade cacheFacade, KsetCacheProperties properties) {
        this.cacheFacade = cacheFacade;
        this.properties = properties;
    }

    @Override
    public Cache getCache(String name) {
        return caches.computeIfAbsent(name, cacheName -> new KsetSpringCache(cacheName, cacheFacade, properties));
    }

    @Override
    public Collection<String> getCacheNames() {
        return Collections.unmodifiableSet(caches.keySet());
    }
}
