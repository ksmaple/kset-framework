package com.kset.cache.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kset.cache.config.KsetCacheProperties;
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
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

public class DefaultKsetCacheFacade implements KsetCacheFacade {

    private static final Logger log = LoggerFactory.getLogger(DefaultKsetCacheFacade.class);

    private final Map<KsetCacheLayer, KsetCacheStore> stores;
    private final KsetCacheProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();
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
        for (KsetCacheLayer layer : readOrder(layers)) {
            Optional<KsetCacheValue> value = getFromLayer(layer, spec);
            if (value.isPresent()) {
                metrics.hit(layer);
                if (layer == KsetCacheLayer.L2 && layers.contains(KsetCacheLayer.L1)) {
                    putToLayer(KsetCacheLayer.L1, spec, value.get());
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
        for (KsetCacheLayer layer : effectiveLayers(spec.layers())) {
            putToLayer(layer, spec, cacheValue);
        }
        metrics.put();
    }

    @Override
    public void evict(KsetCacheSpec spec) {
        for (KsetCacheLayer layer : effectiveLayers(spec.layers())) {
            KsetCacheStore store = stores.get(layer);
            if (store == null) {
                continue;
            }
            try (MonitorTransaction tx = Monitor.newTransaction(MonitorTypes.CACHE, "evict." + layer)) {
                store.evict(spec);
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
        return objectMapper.convertValue(raw, spec.valueType());
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
            loadingTasks.remove(loadKey, task);
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

    private void putToLayer(KsetCacheLayer layer, KsetCacheSpec spec, KsetCacheValue value) {
        KsetCacheStore store = stores.get(layer);
        if (store == null) {
            return;
        }
        Duration ttl = jitter(effectiveTtl(layer, spec, value));
        try (MonitorTransaction tx = Monitor.newTransaction(MonitorTypes.CACHE, "put." + layer)) {
            store.put(spec, value, ttl);
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
}
