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
 * Redis 统一操作抽象（单机/集群由底层连接配置决定）。
 * <p>
 * <b>所有缓存写入必须带有效期</b>：未传 {@code ttl} 的写方法使用 {@code kset.redis.default-ttl}，禁止永久 key。
 */
public interface KsetRedisOperations {

    String sourceName();

    // ── String / Key ──────────────────────────────────────

    <T> T get(String key, Class<T> type);

    <T> T get(String key, TypeReference<T> typeReference);

    /**
     * 写入并使用 {@link KsetRedisTtlPolicy#defaultTtl()}（非永久）。
     */
    void set(String key, Object value);

    void setEx(String key, Object value, Duration ttl);

    /**
     * 仅当 key 不存在时写入，使用默认 TTL。
     */
    Boolean setIfAbsent(String key, Object value);

    Boolean setIfAbsent(String key, Object value, Duration ttl);

    Boolean delete(String key);

    /**
     * 按模式删除：SCAN 流式遍历 + 分批 UNLINK/DEL（非 KEYS）。
     */
    long deleteByPattern(String pattern);

    /**
     * 流式扫描 key，供调用方分批处理（避免一次性加载）。
     *
     * @return 扫描到的 key 数量
     */
    long scanKeys(String pattern, Consumer<String> keyConsumer);

    Boolean exists(String key);

    Boolean expire(String key, Duration ttl);

    Long ttl(String key);

    Long increment(String key);

    Long increment(String key, long delta);

    /**
     * 计数并刷新/设置 key 过期时间。
     */
    Long increment(String key, long delta, Duration ttl);

    Long decrement(String key);

    Long decrement(String key, long delta);

    /**
     * 批量获取；集群下同 slot 限制仍适用。
     */
    List<Object> mget(Collection<String> keys);

    /**
     * 分块 MGET，降低大 key 集合下的阻塞风险。
     */
    List<Object> mgetChunked(Collection<String> keys);

    /**
     * 批量读取为 Map（key 与入参顺序一致；未命中 value 为 {@code null}）。集群下同 slot 限制仍适用。
     */
    <T> Map<String, T> multiGet(Collection<String> keys, Class<T> type);

    <T> Map<String, T> multiGet(Collection<String> keys, TypeReference<T> typeReference);

    /**
     * 批量写入，统一使用 {@link KsetRedisTtlPolicy#defaultTtl()}。
     */
    void multiSet(Map<String, ?> entries);

    void multiSet(Map<String, ?> entries, Duration ttl);

    /**
     * 批量删除 key（分块 DEL/UNLINK）。
     *
     * @return 实际删除的 key 数量
     */
    long deleteAll(Collection<String> keys);

    /**
     * 批量判断 key 是否存在（分块查询）。
     */
    Map<String, Boolean> existsAll(Collection<String> keys);

    /**
     * 批量刷新过期时间（Pipeline）。
     */
    void expireAll(Collection<String> keys, Duration ttl);

    // ── Hash ──────────────────────────────────────────────

    <T> T hGet(String key, String field, Class<T> type);

    void hSet(String key, String field, Object value);

    void hSet(String key, String field, Object value, Duration ttl);

    /**
     * 大 Hash 慎用，优先 {@link #hScan(String, BiConsumer)}。
     */
    @Deprecated
    Map<Object, Object> hGetAll(String key);

    void hScan(String key, BiConsumer<String, Object> fieldConsumer);

    Long hDel(String key, Object... fields);

    Boolean hExists(String key, String field);

    /**
     * 批量写字段并刷新 Hash key 的 TTL。
     */
    void hSetAll(String key, Map<String, ?> fieldValues);

    void hSetAll(String key, Map<String, ?> fieldValues, Duration ttl);

    /**
     * 批量读 Hash 字段（HMGET）。
     */
    <T> Map<String, T> hMGet(String key, Collection<String> fields, Class<T> type);

    // ── List ──────────────────────────────────────────────

    Long lPush(String key, Object... values);

    Long lPush(String key, Duration ttl, Object... values);

    <T> T rPop(String key, Class<T> type);

    List<Object> lRange(String key, long start, long end);

    Long lLen(String key);

    // ── Set ───────────────────────────────────────────────

    Long sAdd(String key, Object... values);

    Long sAdd(String key, Duration ttl, Object... values);

    /**
     * 大 Set 慎用，优先 {@link #sScan(String, Consumer)}。
     */
    @Deprecated
    Set<Object> sMembers(String key);

    void sScan(String key, Consumer<Object> memberConsumer);

    Long sRem(String key, Object... values);

    Boolean sIsMember(String key, Object value);
}
