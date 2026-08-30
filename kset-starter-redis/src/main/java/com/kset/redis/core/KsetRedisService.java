package com.kset.redis.core;

import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * 可注入的 Redis 服务门面，委托 {@link KsetRedisOperations}。
 */
public class KsetRedisService implements KsetRedisOperations {

    private final String name;
    private final KsetRedisOperations delegate;

    public KsetRedisService(String name, KsetRedisOperations delegate) {
        this.name = name;
        this.delegate = delegate;
    }

    public static KsetRedisService from(RedisTemplate<String, Object> template) {
        return from(KsetRedisRegistry.PRIMARY_NAME, template,
                new KsetRedisTtlPolicy(new com.kset.redis.config.KsetRedisProperties()),
                new KsetRedisStreamSettings(new com.kset.redis.config.KsetRedisProperties()));
    }

    public static KsetRedisService from(String name, RedisTemplate<String, Object> template) {
        return from(name, template,
                new KsetRedisTtlPolicy(new com.kset.redis.config.KsetRedisProperties()),
                new KsetRedisStreamSettings(new com.kset.redis.config.KsetRedisProperties()));
    }

    public static KsetRedisService from(RedisTemplate<String, Object> template,
                                        KsetRedisTtlPolicy ttlPolicy,
                                        KsetRedisStreamSettings streamSettings) {
        return from(KsetRedisRegistry.PRIMARY_NAME, template, ttlPolicy, streamSettings);
    }

    public static KsetRedisService from(String name,
                                        RedisTemplate<String, Object> template,
                                        KsetRedisTtlPolicy ttlPolicy,
                                        KsetRedisStreamSettings streamSettings) {
        return new KsetRedisService(name,
                new KsetRedisTemplateOperations(name, template, ttlPolicy, streamSettings));
    }

    public static KsetRedisService monitoredFrom(String name,
                                                 RedisTemplate<String, Object> template,
                                                 KsetRedisTtlPolicy ttlPolicy,
                                                 KsetRedisStreamSettings streamSettings) {
        KsetRedisOperations operations = new KsetRedisTemplateOperations(name, template, ttlPolicy, streamSettings);
        return new KsetRedisService(name, com.kset.redis.monitor.KsetRedisMonitor.wrapOperations(operations));
    }

    public String getName() {
        return name;
    }

    public KsetRedisOperations delegate() {
        return delegate;
    }

    @Override
    public String sourceName() {
        return delegate.sourceName();
    }

    @Override
    public <T> T get(String key, Class<T> type) {
        return delegate.get(key, type);
    }

    @Override
    public <T> T get(String key, TypeReference<T> typeReference) {
        return delegate.get(key, typeReference);
    }

    @Override
    public void set(String key, Object value) {
        delegate.set(key, value);
    }

    @Override
    public void setEx(String key, Object value, Duration ttl) {
        delegate.setEx(key, value, ttl);
    }

    @Override
    public Boolean setIfAbsent(String key, Object value) {
        return delegate.setIfAbsent(key, value);
    }

    @Override
    public Boolean setIfAbsent(String key, Object value, Duration ttl) {
        return delegate.setIfAbsent(key, value, ttl);
    }

    @Override
    public Boolean delete(String key) {
        return delegate.delete(key);
    }

    @Override
    public long deleteByPattern(String pattern) {
        return delegate.deleteByPattern(pattern);
    }

    @Override
    public long scanKeys(String pattern, Consumer<String> keyConsumer) {
        return delegate.scanKeys(pattern, keyConsumer);
    }

    @Override
    public Boolean exists(String key) {
        return delegate.exists(key);
    }

    @Override
    public Boolean expire(String key, Duration ttl) {
        return delegate.expire(key, ttl);
    }

    @Override
    public Long ttl(String key) {
        return delegate.ttl(key);
    }

    @Override
    public Long increment(String key) {
        return delegate.increment(key);
    }

    @Override
    public Long increment(String key, long delta) {
        return delegate.increment(key, delta);
    }

    @Override
    public Long increment(String key, long delta, Duration ttl) {
        return delegate.increment(key, delta, ttl);
    }

    @Override
    public Long decrement(String key) {
        return delegate.decrement(key);
    }

    @Override
    public Long decrement(String key, long delta) {
        return delegate.decrement(key, delta);
    }

    @Override
    public List<Object> mget(Collection<String> keys) {
        return delegate.mget(keys);
    }

    @Override
    public List<Object> mgetChunked(Collection<String> keys) {
        return delegate.mgetChunked(keys);
    }

    @Override
    public <T> Map<String, T> multiGet(Collection<String> keys, Class<T> type) {
        return delegate.multiGet(keys, type);
    }

    @Override
    public <T> Map<String, T> multiGet(Collection<String> keys, TypeReference<T> typeReference) {
        return delegate.multiGet(keys, typeReference);
    }

    @Override
    public void multiSet(Map<String, ?> entries) {
        delegate.multiSet(entries);
    }

    @Override
    public void multiSet(Map<String, ?> entries, Duration ttl) {
        delegate.multiSet(entries, ttl);
    }

    @Override
    public long deleteAll(Collection<String> keys) {
        return delegate.deleteAll(keys);
    }

    @Override
    public Map<String, Boolean> existsAll(Collection<String> keys) {
        return delegate.existsAll(keys);
    }

    @Override
    public void expireAll(Collection<String> keys, Duration ttl) {
        delegate.expireAll(keys, ttl);
    }

    @Override
    public <T> T hGet(String key, String field, Class<T> type) {
        return delegate.hGet(key, field, type);
    }

    @Override
    public void hSet(String key, String field, Object value) {
        delegate.hSet(key, field, value);
    }

    @Override
    public void hSet(String key, String field, Object value, Duration ttl) {
        delegate.hSet(key, field, value, ttl);
    }

    @Override
    @Deprecated
    public Map<Object, Object> hGetAll(String key) {
        return delegate.hGetAll(key);
    }

    @Override
    public void hScan(String key, BiConsumer<String, Object> fieldConsumer) {
        delegate.hScan(key, fieldConsumer);
    }

    @Override
    public Long hDel(String key, Object... fields) {
        return delegate.hDel(key, fields);
    }

    @Override
    public Boolean hExists(String key, String field) {
        return delegate.hExists(key, field);
    }

    @Override
    public void hSetAll(String key, Map<String, ?> fieldValues) {
        delegate.hSetAll(key, fieldValues);
    }

    @Override
    public void hSetAll(String key, Map<String, ?> fieldValues, Duration ttl) {
        delegate.hSetAll(key, fieldValues, ttl);
    }

    @Override
    public <T> Map<String, T> hMGet(String key, Collection<String> fields, Class<T> type) {
        return delegate.hMGet(key, fields, type);
    }

    @Override
    public Long lPush(String key, Object... values) {
        return delegate.lPush(key, values);
    }

    @Override
    public Long lPush(String key, Duration ttl, Object... values) {
        return delegate.lPush(key, ttl, values);
    }

    @Override
    public <T> T rPop(String key, Class<T> type) {
        return delegate.rPop(key, type);
    }

    @Override
    public List<Object> lRange(String key, long start, long end) {
        return delegate.lRange(key, start, end);
    }

    @Override
    public Long lLen(String key) {
        return delegate.lLen(key);
    }

    @Override
    public Long sAdd(String key, Object... values) {
        return delegate.sAdd(key, values);
    }

    @Override
    public Long sAdd(String key, Duration ttl, Object... values) {
        return delegate.sAdd(key, ttl, values);
    }

    @Override
    @Deprecated
    public Set<Object> sMembers(String key) {
        return delegate.sMembers(key);
    }

    @Override
    public void sScan(String key, Consumer<Object> memberConsumer) {
        delegate.sScan(key, memberConsumer);
    }

    @Override
    public Long sRem(String key, Object... values) {
        return delegate.sRem(key, values);
    }

    @Override
    public Boolean sIsMember(String key, Object value) {
        return delegate.sIsMember(key, value);
    }

    @Override
    public RedisTemplate<String, Object> template() {
        return delegate.template();
    }
}
