package com.kset.cache.interceptor;

import com.kset.cache.core.KsetCacheLayer;

import java.util.List;

public final class KsetCacheOperation {

    public enum Kind {
        CACHEABLE,
        PUT,
        EVICT
    }

    private final Kind kind;
    private final List<String> cacheNames;
    private final String key;
    private final String keyGenerator;
    private final List<KsetCacheLayer> layers;
    private final String ttl;
    private final String nullTtl;
    private final boolean cacheNull;
    private final String condition;
    private final String unless;
    private final boolean allEntries;
    private final boolean beforeInvocation;

    KsetCacheOperation(Kind kind,
                       List<String> cacheNames,
                       String key,
                       String keyGenerator,
                       List<KsetCacheLayer> layers,
                       String ttl,
                       String nullTtl,
                       boolean cacheNull,
                       String condition,
                       String unless,
                       boolean allEntries,
                       boolean beforeInvocation) {
        this.kind = kind;
        this.cacheNames = List.copyOf(cacheNames);
        this.key = key;
        this.keyGenerator = keyGenerator;
        this.layers = layers;
        this.ttl = ttl;
        this.nullTtl = nullTtl;
        this.cacheNull = cacheNull;
        this.condition = condition;
        this.unless = unless;
        this.allEntries = allEntries;
        this.beforeInvocation = beforeInvocation;
    }

    public Kind kind() {
        return kind;
    }

    List<String> cacheNames() {
        return cacheNames;
    }

    String key() {
        return key;
    }

    String keyGenerator() {
        return keyGenerator;
    }

    public List<KsetCacheLayer> layers() {
        return layers;
    }

    String ttl() {
        return ttl;
    }

    String nullTtl() {
        return nullTtl;
    }

    boolean cacheNull() {
        return cacheNull;
    }

    String condition() {
        return condition;
    }

    String unless() {
        return unless;
    }

    boolean allEntries() {
        return allEntries;
    }

    boolean beforeInvocation() {
        return beforeInvocation;
    }
}
