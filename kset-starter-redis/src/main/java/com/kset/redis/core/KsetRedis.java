package com.kset.redis.core;

import com.fasterxml.jackson.core.type.TypeReference;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Redis 静态门面，委托 {@link KsetRedisRegistry#primary()} 或命名数据源。
 * <p>
 * 须在 Spring 容器启动并由 {@link com.kset.redis.support.KsetRedisBootstrap} 绑定 Registry 后使用。
 */
public final class KsetRedis {

    private static volatile KsetRedisRegistry registry;

    private KsetRedis() {
    }

    public static void bind(KsetRedisRegistry bound) {
        registry = bound;
    }

    public static void unbind() {
        registry = null;
    }

    public static KsetRedisOperations of(String sourceName) {
        return requireRegistry().get(sourceName);
    }

    public static KsetRedisOperations primary() {
        return requireRegistry().primary();
    }

    private static KsetRedisRegistry requireRegistry() {
        KsetRedisRegistry r = registry;
        if (r == null) {
            throw new IllegalStateException(
                    "KsetRedis is not initialized; inject KsetRedisService or wait for application context startup");
        }
        return r;
    }

    private static KsetRedisOperations ops() {
        return requireRegistry().primary();
    }

    public static <T> T get(String key, Class<T> type) {
        return ops().get(key, type);
    }

    public static <T> T get(String key, TypeReference<T> typeReference) {
        return ops().get(key, typeReference);
    }

    public static void set(String key, Object value) {
        ops().set(key, value);
    }

    public static void setEx(String key, Object value, Duration ttl) {
        ops().setEx(key, value, ttl);
    }

    public static Boolean setIfAbsent(String key, Object value) {
        return ops().setIfAbsent(key, value);
    }

    public static Boolean setIfAbsent(String key, Object value, Duration ttl) {
        return ops().setIfAbsent(key, value, ttl);
    }

    public static Boolean delete(String key) {
        return ops().delete(key);
    }

    public static long deleteByPattern(String pattern) {
        return ops().deleteByPattern(pattern);
    }

    public static Boolean exists(String key) {
        return ops().exists(key);
    }

    public static Boolean expire(String key, Duration ttl) {
        return ops().expire(key, ttl);
    }

    public static Long ttl(String key) {
        return ops().ttl(key);
    }

    public static Long increment(String key) {
        return ops().increment(key);
    }

    public static Long increment(String key, long delta) {
        return ops().increment(key, delta);
    }

    public static Long decrement(String key) {
        return ops().decrement(key);
    }

    public static List<Object> mget(Collection<String> keys) {
        return ops().mget(keys);
    }

    public static <T> Map<String, T> multiGet(Collection<String> keys, Class<T> type) {
        return ops().multiGet(keys, type);
    }

    public static void multiSet(Map<String, ?> entries) {
        ops().multiSet(entries);
    }

    public static void multiSet(Map<String, ?> entries, Duration ttl) {
        ops().multiSet(entries, ttl);
    }

    public static long deleteAll(Collection<String> keys) {
        return ops().deleteAll(keys);
    }

    public static Map<String, Boolean> existsAll(Collection<String> keys) {
        return ops().existsAll(keys);
    }

    public static void expireAll(Collection<String> keys, Duration ttl) {
        ops().expireAll(keys, ttl);
    }

    public static <T> T hGet(String key, String field, Class<T> type) {
        return ops().hGet(key, field, type);
    }

    public static void hSet(String key, String field, Object value) {
        ops().hSet(key, field, value);
    }

    /**
     * 大 Hash 慎用，优先流式 {@code hScan}。
     */
    @Deprecated
    public static Map<Object, Object> hGetAll(String key) {
        return ops().hGetAll(key);
    }

    public static Long hDel(String key, Object... fields) {
        return ops().hDel(key, fields);
    }

    public static Long lPush(String key, Object... values) {
        return ops().lPush(key, values);
    }

    public static <T> T rPop(String key, Class<T> type) {
        return ops().rPop(key, type);
    }

    public static Long sAdd(String key, Object... values) {
        return ops().sAdd(key, values);
    }

    /**
     * 大 Set 慎用，优先流式 {@code sScan}。
     */
    @Deprecated
    public static Set<Object> sMembers(String key) {
        return ops().sMembers(key);
    }

    public static long scanKeys(String pattern, Consumer<String> keyConsumer) {
        return ops().scanKeys(pattern, keyConsumer);
    }

    public static void hScan(String key, BiConsumer<String, Object> fieldConsumer) {
        ops().hScan(key, fieldConsumer);
    }

    public static void sScan(String key, Consumer<Object> memberConsumer) {
        ops().sScan(key, memberConsumer);
    }
}
