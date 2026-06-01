package com.kset.cache.core;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Function;

public class NoOpKsetCacheFacade implements KsetCacheFacade {

    @Override
    public Optional<KsetCacheValue> get(KsetCacheSpec spec) {
        return Optional.empty();
    }

    @Override
    public void put(KsetCacheSpec spec, Object value) {
    }

    @Override
    public void evict(KsetCacheSpec spec) {
    }

    @Override
    public void clear(String cacheName) {
    }

    @Override
    public void clear(String cacheName, List<KsetCacheLayer> layers) {
    }

    @Override
    public Object getOrLoad(List<KsetCacheSpec> specs, Callable<Object> loader) throws Exception {
        return loader.call();
    }

    @Override
    public Object getOrLoad(List<KsetCacheSpec> specs,
                            Callable<Object> loader,
                            Function<Object, List<KsetCacheSpec>> writeSpecSelector) throws Exception {
        return loader.call();
    }

    @Override
    public <T> Optional<T> getValue(KsetCacheSpec spec, Class<T> type) {
        return Optional.empty();
    }

    @Override
    public <T> T getOrLoadValue(KsetCacheSpec spec, Class<T> type, Callable<T> loader) throws Exception {
        return loader.call();
    }

    @Override
    public KsetCacheMetrics metrics() {
        return new KsetCacheMetrics(0, 0, 0, 0, 0, 0, 0);
    }
}
