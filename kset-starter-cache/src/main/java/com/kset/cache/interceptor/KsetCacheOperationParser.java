package com.kset.cache.interceptor;

import com.kset.cache.annotation.KsetCacheEvict;
import com.kset.cache.annotation.KsetCachePut;
import com.kset.cache.annotation.KsetCacheable;
import com.kset.cache.annotation.KsetCaching;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class KsetCacheOperationParser {

    private KsetCacheOperationParser() {
    }

    public static List<KsetCacheOperation> parse(Method method) {
        List<KsetCacheOperation> operations = new ArrayList<>();
        KsetCacheable cacheable = AnnotatedElementUtils.findMergedAnnotation(method, KsetCacheable.class);
        if (cacheable != null) {
            operations.add(cacheable(cacheable));
        }
        KsetCachePut put = AnnotatedElementUtils.findMergedAnnotation(method, KsetCachePut.class);
        if (put != null) {
            operations.add(put(put));
        }
        KsetCacheEvict evict = AnnotatedElementUtils.findMergedAnnotation(method, KsetCacheEvict.class);
        if (evict != null) {
            operations.add(evict(evict));
        }
        KsetCaching caching = AnnotatedElementUtils.findMergedAnnotation(method, KsetCaching.class);
        if (caching != null) {
            Arrays.stream(caching.cacheable()).map(KsetCacheOperationParser::cacheable).forEach(operations::add);
            Arrays.stream(caching.put()).map(KsetCacheOperationParser::put).forEach(operations::add);
            Arrays.stream(caching.evict()).map(KsetCacheOperationParser::evict).forEach(operations::add);
        }
        return operations;
    }

    private static KsetCacheOperation cacheable(KsetCacheable annotation) {
        return new KsetCacheOperation(KsetCacheOperation.Kind.CACHEABLE,
                annotation.cacheName(),
                annotation.key(),
                List.of(annotation.layers()),
                annotation.ttl(),
                annotation.nullTtl(),
                annotation.cacheNull(),
                false);
    }

    private static KsetCacheOperation put(KsetCachePut annotation) {
        return new KsetCacheOperation(KsetCacheOperation.Kind.PUT,
                annotation.cacheName(),
                annotation.key(),
                List.of(annotation.layers()),
                annotation.ttl(),
                annotation.nullTtl(),
                annotation.cacheNull(),
                false);
    }

    private static KsetCacheOperation evict(KsetCacheEvict annotation) {
        return new KsetCacheOperation(KsetCacheOperation.Kind.EVICT,
                annotation.cacheName(),
                annotation.key(),
                List.of(annotation.layers()),
                "",
                "",
                true,
                annotation.beforeInvocation());
    }
}
