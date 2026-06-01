package com.kset.cache.interceptor;

import com.kset.cache.annotation.KsetCacheConfig;
import com.kset.cache.annotation.KsetCacheEvict;
import com.kset.cache.annotation.KsetCachePut;
import com.kset.cache.annotation.KsetCacheable;
import com.kset.cache.annotation.KsetCaching;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class KsetCacheOperationParser {

    private KsetCacheOperationParser() {
    }

    public static List<KsetCacheOperation> parse(Method method) {
        List<KsetCacheOperation> operations = new ArrayList<>();
        KsetCacheConfig config = AnnotatedElementUtils.findMergedAnnotation(method.getDeclaringClass(), KsetCacheConfig.class);
        KsetCacheable cacheable = AnnotatedElementUtils.findMergedAnnotation(method, KsetCacheable.class);
        if (cacheable != null) {
            operations.add(cacheable(cacheable, config));
        }
        KsetCachePut put = AnnotatedElementUtils.findMergedAnnotation(method, KsetCachePut.class);
        if (put != null) {
            operations.add(put(put, config));
        }
        KsetCacheEvict evict = AnnotatedElementUtils.findMergedAnnotation(method, KsetCacheEvict.class);
        if (evict != null) {
            operations.add(evict(evict, config));
        }
        KsetCaching caching = AnnotatedElementUtils.findMergedAnnotation(method, KsetCaching.class);
        if (caching != null) {
            Arrays.stream(caching.cacheable()).map(annotation -> cacheable(annotation, config)).forEach(operations::add);
            Arrays.stream(caching.put()).map(annotation -> put(annotation, config)).forEach(operations::add);
            Arrays.stream(caching.evict()).map(annotation -> evict(annotation, config)).forEach(operations::add);
        }
        return operations;
    }

    private static KsetCacheOperation cacheable(KsetCacheable annotation, KsetCacheConfig config) {
        return new KsetCacheOperation(KsetCacheOperation.Kind.CACHEABLE,
                cacheNames(annotation.cacheName(), annotation.cacheNames(), config),
                annotation.key(),
                defaultText(annotation.keyGenerator(), config != null ? config.keyGenerator() : ""),
                layers(annotation.layers(), config),
                annotation.ttl(),
                annotation.nullTtl(),
                annotation.cacheNull(),
                annotation.condition(),
                annotation.unless(),
                false,
                false);
    }

    private static KsetCacheOperation put(KsetCachePut annotation, KsetCacheConfig config) {
        return new KsetCacheOperation(KsetCacheOperation.Kind.PUT,
                cacheNames(annotation.cacheName(), annotation.cacheNames(), config),
                annotation.key(),
                defaultText(annotation.keyGenerator(), config != null ? config.keyGenerator() : ""),
                layers(annotation.layers(), config),
                annotation.ttl(),
                annotation.nullTtl(),
                annotation.cacheNull(),
                annotation.condition(),
                annotation.unless(),
                false,
                false);
    }

    private static KsetCacheOperation evict(KsetCacheEvict annotation, KsetCacheConfig config) {
        return new KsetCacheOperation(KsetCacheOperation.Kind.EVICT,
                cacheNames(annotation.cacheName(), annotation.cacheNames(), config),
                annotation.key(),
                defaultText(annotation.keyGenerator(), config != null ? config.keyGenerator() : ""),
                layers(annotation.layers(), config),
                "",
                "",
                true,
                annotation.condition(),
                "",
                annotation.allEntries(),
                annotation.beforeInvocation());
    }

    private static List<String> cacheNames(String cacheName, String[] cacheNames, KsetCacheConfig config) {
        Set<String> names = new LinkedHashSet<>();
        if (cacheName != null && !cacheName.isBlank()) {
            names.add(cacheName);
        }
        Arrays.stream(cacheNames)
                .filter(name -> name != null && !name.isBlank())
                .forEach(names::add);
        if (names.isEmpty() && config != null) {
            if (config.cacheName() != null && !config.cacheName().isBlank()) {
                names.add(config.cacheName());
            }
            Arrays.stream(config.cacheNames())
                    .filter(name -> name != null && !name.isBlank())
                    .forEach(names::add);
        }
        if (names.isEmpty()) {
            throw new IllegalArgumentException("cacheName or cacheNames must not be blank");
        }
        return List.copyOf(names);
    }

    private static String defaultText(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static List<com.kset.cache.core.KsetCacheLayer> layers(com.kset.cache.core.KsetCacheLayer[] layers,
                                                                    KsetCacheConfig config) {
        if (layers.length > 0) {
            return List.of(layers);
        }
        if (config != null && config.layers().length > 0) {
            return List.of(config.layers());
        }
        return List.of();
    }
}
