package com.kset.redis.key;

/**
 * 推荐的 Redis Key 业务域段（作为 {@link KsetRedisKeys} 的 module 段）。
 */
public final class KsetRedisKeyNamespace {

    public static final String CACHE = "cache";
    public static final String LOCK = "lock";
    public static final String RANK = "rank";

    private KsetRedisKeyNamespace() {
    }
}
