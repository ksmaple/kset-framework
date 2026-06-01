package com.kset.cache.spring;

import com.kset.cache.config.KsetCacheProperties;
import com.kset.cache.core.KsetCacheFacade;
import com.kset.cache.core.KsetCacheLayer;
import com.kset.cache.core.KsetCacheSpec;
import org.springframework.cache.Cache;
import org.springframework.cache.support.SimpleValueWrapper;

import java.util.List;
import java.util.concurrent.Callable;

public class KsetSpringCache implements Cache {

    private final String name;
    private final KsetCacheFacade cacheFacade;
    private final KsetCacheProperties properties;

    public KsetSpringCache(String name, KsetCacheFacade cacheFacade, KsetCacheProperties properties) {
        this.name = name;
        this.cacheFacade = cacheFacade;
        this.properties = properties;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Object getNativeCache() {
        return cacheFacade;
    }

    @Override
    public ValueWrapper get(Object key) {
        return cacheFacade.getValue(spec(key, Object.class), Object.class)
                .map(SimpleValueWrapper::new)
                .orElse(null);
    }

    @Override
    public <T> T get(Object key, Class<T> type) {
        return cacheFacade.getValue(spec(key, type), type).orElse(null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Object key, Callable<T> valueLoader) {
        try {
            Object value = cacheFacade.getOrLoad(List.of(spec(key, Object.class)), () -> valueLoader.call());
            return (T) value;
        } catch (Exception e) {
            throw new ValueRetrievalException(key, valueLoader, e);
        }
    }

    @Override
    public void put(Object key, Object value) {
        cacheFacade.put(spec(key, value != null ? value.getClass() : Object.class), value);
    }

    @Override
    public void evict(Object key) {
        cacheFacade.evict(spec(key, Object.class));
    }

    @Override
    public void clear() {
        cacheFacade.clear(name);
    }

    private KsetCacheSpec spec(Object key, Class<?> valueType) {
        List<KsetCacheLayer> layers = properties.getDefaultLayers();
        return new KsetCacheSpec(
                name,
                String.valueOf(key),
                layers,
                null,
                properties.getNullTtl(),
                properties.isCacheNull(),
                valueType);
    }
}
