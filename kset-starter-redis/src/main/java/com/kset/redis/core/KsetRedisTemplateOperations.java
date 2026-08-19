package com.kset.redis.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.kset.common.utils.JsonUtil;
import com.kset.common.utils.collection.ListHelper;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.SessionCallback;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * 基于 {@link RedisTemplate} 的 {@link KsetRedisOperations} 默认实现。
 */
public class KsetRedisTemplateOperations implements KsetRedisOperations {

    private final String sourceName;
    private final RedisTemplate<String, Object> template;
    private final KsetRedisTtlPolicy ttlPolicy;
    private final KsetRedisStreamSettings streamSettings;

    public KsetRedisTemplateOperations(String sourceName,
                                       RedisTemplate<String, Object> template,
                                       KsetRedisTtlPolicy ttlPolicy,
                                       KsetRedisStreamSettings streamSettings) {
        this.sourceName = sourceName != null ? sourceName : KsetRedisRegistry.PRIMARY_NAME;
        this.template = Objects.requireNonNull(template, "template");
        this.ttlPolicy = Objects.requireNonNull(ttlPolicy, "ttlPolicy");
        this.streamSettings = Objects.requireNonNull(streamSettings, "streamSettings");
    }

    public KsetRedisTemplateOperations(RedisTemplate<String, Object> template,
                                       KsetRedisTtlPolicy ttlPolicy,
                                       KsetRedisStreamSettings streamSettings) {
        this(KsetRedisRegistry.PRIMARY_NAME, template, ttlPolicy, streamSettings);
    }

    @Override
    public String sourceName() {
        return sourceName;
    }

    @Override
    public <T> T get(String key, Class<T> type) {
        Object raw = template.opsForValue().get(key);
        return convert(raw, type);
    }

    @Override
    public <T> T get(String key, TypeReference<T> typeReference) {
        Object raw = template.opsForValue().get(key);
        if (raw == null) {
            return null;
        }
        return convert(raw, typeReference);
    }

    @Override
    public void set(String key, Object value) {
        setEx(key, value, ttlPolicy.defaultTtl());
    }

    @Override
    public void setEx(String key, Object value, Duration ttl) {
        template.opsForValue().set(key, value, ttlPolicy.requireTtl(ttl));
    }

    @Override
    public Boolean setIfAbsent(String key, Object value) {
        return setIfAbsent(key, value, ttlPolicy.defaultTtl());
    }

    @Override
    public Boolean setIfAbsent(String key, Object value, Duration ttl) {
        return template.opsForValue().setIfAbsent(key, value, ttlPolicy.requireTtl(ttl));
    }

    @Override
    public Boolean delete(String key) {
        return Boolean.TRUE.equals(template.delete(key));
    }

    @Override
    public long deleteByPattern(String pattern) {
        if (pattern == null || pattern.isBlank()) {
            return 0;
        }
        AtomicLong deleted = new AtomicLong(0);
        ScanOptions options = ScanOptions.scanOptions()
                .match(pattern)
                .count(streamSettings.scanBatchSize())
                .build();
        template.execute((RedisCallback<Void>) connection -> {
            var keyCommands = connection.keyCommands();
            List<byte[]> batchKeys = new ArrayList<>(streamSettings.deleteBatchSize());
            try (Cursor<byte[]> cursor = keyCommands.scan(options)) {
                while (cursor.hasNext()) {
                    batchKeys.add(cursor.next());
                    if (batchKeys.size() >= streamSettings.deleteBatchSize()) {
                        deleted.addAndGet(unlinkOrDel(keyCommands, batchKeys));
                        batchKeys.clear();
                    }
                }
                if (!batchKeys.isEmpty()) {
                    deleted.addAndGet(unlinkOrDel(keyCommands, batchKeys));
                }
            }
            return null;
        });
        return deleted.get();
    }

    @Override
    public long scanKeys(String pattern, Consumer<String> keyConsumer) {
        if (pattern == null || pattern.isBlank() || keyConsumer == null) {
            return 0;
        }
        AtomicLong count = new AtomicLong(0);
        ScanOptions options = ScanOptions.scanOptions()
                .match(pattern)
                .count(streamSettings.scanBatchSize())
                .build();
        template.execute((RedisCallback<Void>) connection -> {
            try (Cursor<byte[]> cursor = connection.keyCommands().scan(options)) {
                while (cursor.hasNext()) {
                    keyConsumer.accept(rawKeyToString(cursor.next()));
                    count.incrementAndGet();
                }
            }
            return null;
        });
        return count.get();
    }

    private long unlinkOrDel(org.springframework.data.redis.connection.RedisKeyCommands keyCommands,
                             List<byte[]> keys) {
        byte[][] array = keys.toArray(new byte[0][]);
        if (streamSettings.useUnlink()) {
            try {
                return keyCommands.unlink(array);
            } catch (Exception ex) {
                return keyCommands.del(array);
            }
        }
        return keyCommands.del(array);
    }

    private String rawKeyToString(byte[] raw) {
        if (template.getStringSerializer() != null) {
            return template.getStringSerializer().deserialize(raw);
        }
        return new String(raw, StandardCharsets.UTF_8);
    }

    @Override
    public Boolean exists(String key) {
        return Boolean.TRUE.equals(template.hasKey(key));
    }

    @Override
    public Boolean expire(String key, Duration ttl) {
        return Boolean.TRUE.equals(template.expire(key, ttlPolicy.requireTtl(ttl)));
    }

    @Override
    public Long ttl(String key) {
        return template.getExpire(key);
    }

    @Override
    public Long increment(String key) {
        return increment(key, 1L, ttlPolicy.defaultTtl());
    }

    @Override
    public Long increment(String key, long delta) {
        return increment(key, delta, ttlPolicy.defaultTtl());
    }

    @Override
    public Long increment(String key, long delta, Duration ttl) {
        Long result = template.opsForValue().increment(key, delta);
        expire(key, ttl);
        return result;
    }

    @Override
    public Long decrement(String key) {
        return increment(key, -1L);
    }

    @Override
    public Long decrement(String key, long delta) {
        return increment(key, -delta);
    }

    @Override
    public List<Object> mget(Collection<String> keys) {
        return mgetChunked(keys);
    }

    @Override
    public List<Object> mgetChunked(Collection<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        List<String> keyList = keys instanceof List<String> list ? list : List.copyOf(keys);
        int chunk = streamSettings.mgetChunkSize();
        List<Object> result = new ArrayList<>(keyList.size());
        ListHelper.forEachBatch(keyList, chunk, slice -> {
            List<Object> part = template.opsForValue().multiGet(slice);
            if (part != null) {
                result.addAll(part);
            }
        });
        return result;
    }

    @Override
    public <T> Map<String, T> multiGet(Collection<String> keys, Class<T> type) {
        return toMultiGetMap(keys, mgetChunked(keys), type);
    }

    @Override
    public <T> Map<String, T> multiGet(Collection<String> keys, TypeReference<T> typeReference) {
        if (keys == null || keys.isEmpty()) {
            return Map.of();
        }
        List<String> keyList = keys instanceof List<String> list ? list : List.copyOf(keys);
        List<Object> values = mgetChunked(keyList);
        Map<String, T> result = LinkedHashMap.newLinkedHashMap(keyList.size());
        for (int i = 0; i < keyList.size(); i++) {
            Object raw = i < values.size() ? values.get(i) : null;
            result.put(keyList.get(i), convert(raw, typeReference));
        }
        return result;
    }

    @Override
    public void multiSet(Map<String, ?> entries) {
        multiSet(entries, ttlPolicy.defaultTtl());
    }

    @Override
    public void multiSet(Map<String, ?> entries, Duration ttl) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        Duration effective = ttlPolicy.requireTtl(ttl);
        forEachChunkedMap(entries, streamSettings.mgetChunkSize(), chunk ->
                template.executePipelined(new SessionCallback<Object>() {
                    @Override
                    @SuppressWarnings({"unchecked", "rawtypes"})
                    public Object execute(RedisOperations operations) {
                        for (Map.Entry<String, ?> entry : chunk.entrySet()) {
                            operations.opsForValue().set(entry.getKey(), entry.getValue(), effective);
                        }
                        return null;
                    }
                }));
    }

    @Override
    public long deleteAll(Collection<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return 0;
        }
        List<String> keyList = keys instanceof List<String> list ? list : List.copyOf(keys);
        long deleted = 0;
        int chunk = streamSettings.deleteBatchSize();
        for (List<String> slice : ListHelper.partition(keyList, chunk)) {
            Long count = template.delete(slice);
            if (count != null) {
                deleted += count;
            }
        }
        return deleted;
    }

    @Override
    public Map<String, Boolean> existsAll(Collection<String> keys) {
        return existsAllPipelined(keys);
    }

    /**
     * 保留原因：1.0.11 对每个 key 单独 hasKey，大批量存在检查 RTT 过高。
     */
    @SuppressWarnings("unused")
    private Map<String, Boolean> existsAllForRollback(Collection<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return Map.of();
        }
        List<String> keyList = keys instanceof List<String> list ? list : List.copyOf(keys);
        Map<String, Boolean> result = LinkedHashMap.newLinkedHashMap(keyList.size());
        int chunk = streamSettings.mgetChunkSize();
        ListHelper.forEachBatch(keyList, chunk, slice -> {
            for (String key : slice) {
                result.put(key, Boolean.TRUE.equals(template.hasKey(key)));
            }
        });
        return result;
    }

    private Map<String, Boolean> existsAllPipelined(Collection<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return Map.of();
        }
        List<String> keyList = keys instanceof List<String> list ? list : List.copyOf(keys);
        Map<String, Boolean> result = LinkedHashMap.newLinkedHashMap(keyList.size());
        int chunk = streamSettings.mgetChunkSize();
        ListHelper.forEachBatch(keyList, chunk, slice -> {
            List<Object> flags = template.executePipelined(new SessionCallback<Object>() {
                @Override
                @SuppressWarnings({"unchecked", "rawtypes"})
                public Object execute(RedisOperations operations) {
                    for (String key : slice) {
                        operations.hasKey(key);
                    }
                    return null;
                }
            });
            for (int i = 0; i < slice.size(); i++) {
                Object flag = flags != null && i < flags.size() ? flags.get(i) : null;
                result.put(slice.get(i), Boolean.TRUE.equals(flag));
            }
        });
        return result;
    }

    @Override
    public void expireAll(Collection<String> keys, Duration ttl) {
        if (keys == null || keys.isEmpty()) {
            return;
        }
        Duration effective = ttlPolicy.requireTtl(ttl);
        List<String> keyList = keys instanceof List<String> list ? list : List.copyOf(keys);
        int chunk = streamSettings.mgetChunkSize();
        ListHelper.forEachBatch(keyList, chunk, slice -> template.executePipelined(new SessionCallback<Object>() {
            @Override
            @SuppressWarnings({"unchecked", "rawtypes"})
            public Object execute(RedisOperations operations) {
                for (String key : slice) {
                    operations.expire(key, effective);
                }
                return null;
            }
        }));
    }

    @Override
    public <T> T hGet(String key, String field, Class<T> type) {
        Object raw = template.opsForHash().get(key, field);
        return convert(raw, type);
    }

    @Override
    public void hSet(String key, String field, Object value) {
        hSet(key, field, value, ttlPolicy.defaultTtl());
    }

    @Override
    public void hSet(String key, String field, Object value, Duration ttl) {
        template.opsForHash().put(key, field, value);
        expire(key, ttl);
    }

    @Override
    @Deprecated
    public Map<Object, Object> hGetAll(String key) {
        return template.opsForHash().entries(key);
    }

    @Override
    public void hScan(String key, BiConsumer<String, Object> fieldConsumer) {
        if (fieldConsumer == null) {
            return;
        }
        ScanOptions options = ScanOptions.scanOptions().count(streamSettings.hashScanCount()).build();
        try (Cursor<Map.Entry<Object, Object>> cursor = template.opsForHash().scan(key, options)) {
            while (cursor.hasNext()) {
                Map.Entry<Object, Object> entry = cursor.next();
                fieldConsumer.accept(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
    }

    @Override
    public Long hDel(String key, Object... fields) {
        return template.opsForHash().delete(key, fields);
    }

    @Override
    public Boolean hExists(String key, String field) {
        return template.opsForHash().hasKey(key, field);
    }

    @Override
    public void hSetAll(String key, Map<String, ?> fieldValues) {
        hSetAll(key, fieldValues, ttlPolicy.defaultTtl());
    }

    @Override
    public void hSetAll(String key, Map<String, ?> fieldValues, Duration ttl) {
        if (fieldValues == null || fieldValues.isEmpty()) {
            return;
        }
        template.opsForHash().putAll(key, new LinkedHashMap<>(fieldValues));
        expire(key, ttl);
    }

    @Override
    public <T> Map<String, T> hMGet(String key, Collection<String> fields, Class<T> type) {
        if (fields == null || fields.isEmpty()) {
            return Map.of();
        }
        List<Object> fieldList = fields.stream().map(f -> (Object) f).toList();
        List<Object> values = template.opsForHash().multiGet(key, fieldList);
        Map<String, T> result = LinkedHashMap.newLinkedHashMap(fields.size());
        int index = 0;
        for (String field : fields) {
            Object raw = values != null && index < values.size() ? values.get(index) : null;
            result.put(field, convert(raw, type));
            index++;
        }
        return result;
    }

    @Override
    public Long lPush(String key, Object... values) {
        return lPush(key, ttlPolicy.defaultTtl(), values);
    }

    @Override
    public Long lPush(String key, Duration ttl, Object... values) {
        if (values == null || values.length == 0) {
            return 0L;
        }
        Long size = template.opsForList().leftPushAll(key, values);
        expire(key, ttl);
        return size;
    }

    @Override
    public <T> T rPop(String key, Class<T> type) {
        Object raw = template.opsForList().rightPop(key);
        return convert(raw, type);
    }

    @Override
    public List<Object> lRange(String key, long start, long end) {
        return template.opsForList().range(key, start, end);
    }

    @Override
    public Long lLen(String key) {
        return template.opsForList().size(key);
    }

    @Override
    public Long sAdd(String key, Object... values) {
        return sAdd(key, ttlPolicy.defaultTtl(), values);
    }

    @Override
    public Long sAdd(String key, Duration ttl, Object... values) {
        if (values == null || values.length == 0) {
            return 0L;
        }
        Long added = template.opsForSet().add(key, values);
        expire(key, ttl);
        return added;
    }

    @Override
    @Deprecated
    public Set<Object> sMembers(String key) {
        return template.opsForSet().members(key);
    }

    @Override
    public void sScan(String key, Consumer<Object> memberConsumer) {
        if (memberConsumer == null) {
            return;
        }
        ScanOptions options = ScanOptions.scanOptions().count(streamSettings.scanBatchSize()).build();
        try (Cursor<Object> cursor = template.opsForSet().scan(key, options)) {
            while (cursor.hasNext()) {
                memberConsumer.accept(cursor.next());
            }
        }
    }

    @Override
    public Long sRem(String key, Object... values) {
        if (values == null || values.length == 0) {
            return 0L;
        }
        return template.opsForSet().remove(key, values);
    }

    @Override
    public Boolean sIsMember(String key, Object value) {
        return Boolean.TRUE.equals(template.opsForSet().isMember(key, value));
    }

    @Override
    public RedisTemplate<String, Object> template() {
        return template;
    }

    private <T> T convert(Object raw, Class<T> type) {
        if (raw == null || type == null) {
            return null;
        }
        if (type.isInstance(raw)) {
            return type.cast(raw);
        }
        if (raw instanceof String text) {
            return convertText(text, type);
        }
        return convertJsonValue(raw, type);
    }

    private <T> T convert(Object raw, TypeReference<T> typeReference) {
        if (raw == null || typeReference == null) {
            return null;
        }
        return convertJsonValue(raw, typeReference);
    }

    private static <T> T convertJsonValue(Object raw, Class<T> type) {
        return JsonUtil.convert(raw, type);
    }

    private static <T> T convertJsonValue(Object raw, TypeReference<T> typeReference) {
        return JsonUtil.convert(raw, typeReference);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T> T convertText(String text, Class<T> type) {
        if (String.class == type) {
            return type.cast(text);
        }
        if (Integer.class == type || int.class == type) {
            return (T) Integer.valueOf(text);
        }
        if (Long.class == type || long.class == type) {
            return (T) Long.valueOf(text);
        }
        if (Double.class == type || double.class == type) {
            return (T) Double.valueOf(text);
        }
        if (Float.class == type || float.class == type) {
            return (T) Float.valueOf(text);
        }
        if (Short.class == type || short.class == type) {
            return (T) Short.valueOf(text);
        }
        if (Byte.class == type || byte.class == type) {
            return (T) Byte.valueOf(text);
        }
        if (Boolean.class == type || boolean.class == type) {
            return (T) Boolean.valueOf(text);
        }
        if (Character.class == type || char.class == type) {
            return (T) Character.valueOf(text.charAt(0));
        }
        if (Enum.class.isAssignableFrom(type)) {
            return (T) Enum.valueOf((Class<Enum>) type.asSubclass(Enum.class), text);
        }
        return convertJsonValue(text, type);
    }

    private <T> Map<String, T> toMultiGetMap(Collection<String> keys, List<Object> values, Class<T> type) {
        if (keys == null || keys.isEmpty()) {
            return Map.of();
        }
        List<String> keyList = keys instanceof List<String> list ? list : List.copyOf(keys);
        Map<String, T> result = LinkedHashMap.newLinkedHashMap(keyList.size());
        for (int i = 0; i < keyList.size(); i++) {
            Object raw = i < values.size() ? values.get(i) : null;
            result.put(keyList.get(i), convert(raw, type));
        }
        return result;
    }

    private static void forEachChunkedMap(Map<String, ?> entries, int chunkSize, Consumer<Map<String, ?>> chunkConsumer) {
        if (entries.size() <= chunkSize) {
            chunkConsumer.accept(entries);
            return;
        }
        Map<String, Object> chunk = LinkedHashMap.newLinkedHashMap(chunkSize);
        for (Map.Entry<String, ?> entry : entries.entrySet()) {
            chunk.put(entry.getKey(), entry.getValue());
            if (chunk.size() >= chunkSize) {
                chunkConsumer.accept(chunk);
                chunk.clear();
            }
        }
        if (!chunk.isEmpty()) {
            chunkConsumer.accept(chunk);
        }
    }
}
