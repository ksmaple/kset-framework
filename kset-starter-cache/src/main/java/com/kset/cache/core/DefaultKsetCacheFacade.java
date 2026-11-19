package com.kset.cache.core;

import com.kset.cache.config.KsetCacheProperties;
import com.kset.common.utils.JsonUtil;
import com.kset.common.monitor.Monitor;
import com.kset.common.monitor.facade.MonitorStatus;
import com.kset.common.monitor.facade.MonitorTransaction;
import com.kset.common.monitor.facade.MonitorTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

public class DefaultKsetCacheFacade implements KsetCacheFacade {

    private static final Logger log = LoggerFactory.getLogger(DefaultKsetCacheFacade.class);

    private final Map<KsetCacheLayer, KsetCacheStore> stores;
    private final KsetCacheProperties properties;
    private final ConcurrentHashMap<String, FutureTask<Object>> loadingTasks = new ConcurrentHashMap<>();
    private final KsetCacheMetricCollector metrics = new KsetCacheMetricCollector();

    public DefaultKsetCacheFacade(List<KsetCacheStore> stores, KsetCacheProperties properties) {
        this.stores = new EnumMap<>(KsetCacheLayer.class);
        stores.stream()
                .sorted(Comparator.comparing(store -> store.layer().ordinal()))
                .forEach(store -> this.stores.put(store.layer(), store));
        this.properties = properties;
    }

    @Override
    public Optional<KsetCacheValue> get(KsetCacheSpec spec) {
        List<KsetCacheLayer> layers = effectiveLayers(spec.layers());
        CacheGeneration generation = cacheGeneration(spec.cacheName(), layers);
        for (KsetCacheLayer layer : readOrder(layers)) {
            if (layer == KsetCacheLayer.L1 && !generation.available()) {
                continue;
            }
            Optional<KsetCacheValue> value = layer == KsetCacheLayer.L1 && generation.value().isPresent()
                    ? getFromVersionedL1(spec, generation.value().getAsLong())
                    : getFromLayer(layer, spec);
            if (value.isPresent()) {
                metrics.hit(layer);
                if (layer == KsetCacheLayer.L2 && layers.contains(KsetCacheLayer.L1)) {
                    backfillL1(spec, value.get(), generation);
                }
                return value;
            }
        }
        metrics.miss();
        return Optional.empty();
    }

    @Override
    public void put(KsetCacheSpec spec, Object value) {
        if (value == null && !spec.cacheNull()) {
            return;
        }
        KsetCacheValue cacheValue = KsetCacheValue.of(value);
        List<KsetCacheLayer> layers = effectiveLayers(spec.layers());
        CacheGeneration generation = cacheGeneration(spec.cacheName(), layers);
        for (KsetCacheLayer layer : layers) {
            if (layer != KsetCacheLayer.L1 || generation.available()) {
                putToLayer(layer, spec, cacheValue, null, generation.value());
            }
        }
        metrics.put();
    }

    @Override
    public void evict(KsetCacheSpec spec) {
        List<KsetCacheLayer> layers = effectiveLayers(spec.layers());
        CacheGeneration generation = cacheGeneration(spec.cacheName(), layers);
        for (KsetCacheLayer layer : layers) {
            KsetCacheStore store = stores.get(layer);
            if (store == null) {
                continue;
            }
            try (MonitorTransaction tx = Monitor.newTransaction(MonitorTypes.CACHE, "evict." + layer)) {
                if (layer == KsetCacheLayer.L1 && !generation.available()) {
                    store.clear(spec.cacheName());
                } else if (layer == KsetCacheLayer.L1 && generation.value().isPresent()) {
                    store.evict(spec, generation.value().getAsLong());
                } else {
                    store.evict(spec);
                }
                tx.setStatus(MonitorStatus.SUCCESS);
                metrics.evict();
            } catch (RuntimeException | Error e) {
                log.warn("cache evict failed layer={} cacheName={} key={}", layer, spec.cacheName(), spec.key(), e);
                Monitor.logError(e, "cache evict failed");
                metrics.error();
            }
        }
    }

    @Override
    public void clear(String cacheName) {
        clear(cacheName, properties.getDefaultLayers());
    }

    @Override
    public void clear(String cacheName, List<KsetCacheLayer> layers) {
        for (KsetCacheLayer layer : effectiveLayers(layers)) {
            KsetCacheStore store = stores.get(layer);
            if (store == null) {
                continue;
            }
            try (MonitorTransaction tx = Monitor.newTransaction(MonitorTypes.CACHE, "clear." + layer)) {
                store.clear(cacheName);
                tx.setStatus(MonitorStatus.SUCCESS);
            } catch (UnsupportedOperationException ignored) {
                log.warn("cache clear unsupported layer={} cacheName={}", layer, cacheName);
            } catch (RuntimeException | Error e) {
                log.warn("cache clear failed layer={} cacheName={}", layer, cacheName, e);
                Monitor.logError(e, "cache clear failed");
                metrics.error();
                throw e;
            }
        }
    }

    @Override
    public Object getOrLoad(List<KsetCacheSpec> specs, Callable<Object> loader) throws Exception {
        return getOrLoad(specs, loader, value -> specs);
    }

    @Override
    public Object getOrLoad(List<KsetCacheSpec> specs,
                            Callable<Object> loader,
                            Function<Object, List<KsetCacheSpec>> writeSpecSelector) throws Exception {
        if (specs == null || specs.isEmpty()) {
            return loader.call();
        }
        for (KsetCacheSpec spec : specs) {
            Optional<KsetCacheValue> value = get(spec);
            if (value.isPresent()) {
                return unwrap(spec, value.get());
            }
        }
        if (!properties.isSingleFlightEnabled()) {
            Object loaded = loader.call();
            metrics.load();
            putAll(writeSpecs(writeSpecSelector, loaded), loaded);
            return loaded;
        }
        return loadSingleFlight(specs, loader, writeSpecSelector);
    }

    private Object unwrap(KsetCacheSpec spec, KsetCacheValue value) {
        Object raw = value.unwrap();
        if (raw == null || spec.valueType() == Object.class || spec.valueType().isInstance(raw)) {
            return raw;
        }
        return JsonUtil.convert(raw, spec.valueType());
    }

    private Object loadSingleFlight(List<KsetCacheSpec> specs,
                                    Callable<Object> loader,
                                    Function<Object, List<KsetCacheSpec>> writeSpecSelector) throws Exception {
        String loadKey = specs.get(0).fullKey();
        FutureTask<Object> newTask = new FutureTask<>(() -> {
            Object loaded = loader.call();
            metrics.load();
            putAll(writeSpecs(writeSpecSelector, loaded), loaded);
            return loaded;
        });
        FutureTask<Object> task = loadingTasks.putIfAbsent(loadKey, newTask);
        if (task == null) {
            task = newTask;
            task.run();
        }
        try {
            return task.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception exception) {
                metrics.error();
                throw exception;
            }
            if (cause instanceof Error error) {
                metrics.error();
                throw error;
            }
            metrics.error();
            throw new IllegalStateException(cause);
        } finally {
            if (task == newTask) {
                loadingTasks.remove(loadKey, task);
            }
        }
    }

    private void putAll(List<KsetCacheSpec> specs, Object value) {
        if (specs == null || specs.isEmpty()) {
            return;
        }
        for (KsetCacheSpec spec : specs) {
            put(spec, value);
        }
    }

    private static List<KsetCacheSpec> writeSpecs(Function<Object, List<KsetCacheSpec>> selector, Object value) {
        if (selector == null) {
            return List.of();
        }
        List<KsetCacheSpec> selected = selector.apply(value);
        return selected != null ? selected : List.of();
    }

    private Optional<KsetCacheValue> getFromLayer(KsetCacheLayer layer, KsetCacheSpec spec) {
        KsetCacheStore store = stores.get(layer);
        if (store == null) {
            return Optional.empty();
        }
        try (MonitorTransaction tx = Monitor.newTransaction(MonitorTypes.CACHE, "get." + layer)) {
            Optional<KsetCacheValue> value = store.get(spec);
            tx.setStatus(MonitorStatus.SUCCESS);
            return value;
        } catch (RuntimeException | Error e) {
            log.warn("cache get failed layer={} cacheName={} key={}", layer, spec.cacheName(), spec.key(), e);
            Monitor.logError(e, "cache get failed");
            metrics.error();
            return Optional.empty();
        }
    }

    private Optional<KsetCacheValue> getFromVersionedL1(KsetCacheSpec spec, long generation) {
        try (MonitorTransaction tx = Monitor.newTransaction(MonitorTypes.CACHE, "get.L1")) {
            Optional<KsetCacheValue> value = stores.get(KsetCacheLayer.L1).get(spec, generation);
            tx.setStatus(MonitorStatus.SUCCESS);
            return value;
        } catch (RuntimeException | Error error) {
            log.warn("cache get failed layer=L1 cacheName={} key={}", spec.cacheName(), spec.key(), error);
            Monitor.logError(error, "cache get failed");
            metrics.error();
            return Optional.empty();
        }
    }

    private CacheGeneration cacheGeneration(String cacheName, List<KsetCacheLayer> layers) {
        if (!layers.contains(KsetCacheLayer.L1) || !layers.contains(KsetCacheLayer.L2)) {
            return new CacheGeneration(true, OptionalLong.empty());
        }
        try {
            return new CacheGeneration(true, stores.get(KsetCacheLayer.L2).namespaceGeneration(cacheName));
        } catch (RuntimeException | Error error) {
            log.warn("cache namespace generation lookup failed cacheName={}", cacheName, error);
            metrics.error();
            return new CacheGeneration(false, OptionalLong.empty());
        }
    }

    private void backfillL1(KsetCacheSpec spec, KsetCacheValue value, CacheGeneration generation) {
        KsetCacheStore l2 = stores.get(KsetCacheLayer.L2);
        if (l2 == null || !generation.available()) {
            return;
        }
        try {
            l2.remainingTtl(spec).filter(ttl -> !ttl.isZero() && !ttl.isNegative())
                    .ifPresent(ttl -> putToLayer(KsetCacheLayer.L1, spec, value, ttl, generation.value()));
        } catch (RuntimeException | Error e) {
            log.warn("cache remaining TTL lookup failed cacheName={} key={}", spec.cacheName(), spec.key(), e);
            metrics.error();
        }
    }

    private void putToLayer(KsetCacheLayer layer, KsetCacheSpec spec,
                            KsetCacheValue value, Duration maximumTtl, OptionalLong generation) {
        KsetCacheStore store = stores.get(layer);
        if (store == null) {
            return;
        }
        Duration ttl = jitter(effectiveTtl(layer, spec, value));
        if (maximumTtl != null && (ttl == null || ttl.compareTo(maximumTtl) > 0)) {
            ttl = maximumTtl;
        }
        try (MonitorTransaction tx = Monitor.newTransaction(MonitorTypes.CACHE, "put." + layer)) {
            if (generation.isPresent()) {
                store.put(spec, value, ttl, generation.getAsLong());
            } else {
                store.put(spec, value, ttl);
            }
            tx.setStatus(MonitorStatus.SUCCESS);
        } catch (RuntimeException | Error e) {
            log.warn("cache put failed layer={} cacheName={} key={}", layer, spec.cacheName(), spec.key(), e);
            Monitor.logError(e, "cache put failed");
            metrics.error();
        }
    }

    private Duration effectiveTtl(KsetCacheLayer layer, KsetCacheSpec spec, KsetCacheValue value) {
        if (value.isNullValue()) {
            return spec.nullTtl() != null ? spec.nullTtl() : properties.getNullTtl();
        }
        if (spec.ttl() != null) {
            return spec.ttl();
        }
        return layer == KsetCacheLayer.L1
                ? properties.getL1().getDefaultTtl()
                : properties.getL2().getDefaultTtl();
    }

    private static List<KsetCacheLayer> readOrder(List<KsetCacheLayer> layers) {
        return layers.stream()
                .sorted(Comparator.comparing(KsetCacheLayer::ordinal))
                .toList();
    }

    private Duration jitter(Duration ttl) {
        if (!properties.isTtlJitterEnabled()
                || ttl == null
                || ttl.isZero()
                || ttl.isNegative()
                || properties.getTtlJitterPercent() <= 0) {
            return ttl;
        }
        long baseNanos;
        try {
            baseNanos = ttl.toNanos();
        } catch (ArithmeticException ignored) {
            return ttl;
        }
        long maxExtra = baseNanos / 100 * properties.getTtlJitterPercent();
        if (maxExtra <= 0) {
            return ttl;
        }
        long extra = ThreadLocalRandom.current().nextLong(maxExtra + 1);
        try {
            return ttl.plusNanos(extra);
        } catch (ArithmeticException ignored) {
            return ttl;
        }
    }

    private List<KsetCacheLayer> effectiveLayers(List<KsetCacheLayer> requestedLayers) {
        List<KsetCacheLayer> available = requestedLayers.stream()
                .filter(stores::containsKey)
                .toList();
        if (!available.isEmpty()) {
            return available;
        }
        if (stores.containsKey(KsetCacheLayer.L1)) {
            return List.of(KsetCacheLayer.L1);
        }
        return List.of();
    }

    @Override
    public <T> Optional<T> getValue(KsetCacheSpec spec, Class<T> type) {
        KsetCacheSpec typed = new KsetCacheSpec(
                spec.cacheName(),
                spec.key(),
                spec.layers(),
                spec.ttl(),
                spec.nullTtl(),
                spec.cacheNull(),
                type);
        return get(typed).map(value -> type.cast(unwrap(typed, value)));
    }

    @Override
    public <T> T getOrLoadValue(KsetCacheSpec spec, Class<T> type, Callable<T> loader) throws Exception {
        KsetCacheSpec typed = new KsetCacheSpec(
                spec.cacheName(),
                spec.key(),
                spec.layers(),
                spec.ttl(),
                spec.nullTtl(),
                spec.cacheNull(),
                type);
        return type.cast(getOrLoad(List.of(typed), () -> loader.call()));
    }

    @Override
    public KsetCacheMetrics metrics() {
        return metrics.snapshot();
    }

    private record CacheGeneration(boolean available, OptionalLong value) {
    }
}
