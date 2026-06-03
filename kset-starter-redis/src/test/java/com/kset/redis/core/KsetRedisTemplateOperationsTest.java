package com.kset.redis.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KsetRedisTemplateOperationsTest {

    @Mock
    private RedisTemplate<String, Object> template;
    @Mock
    private ValueOperations<String, Object> valueOps;
    @Mock
    private HashOperations<String, Object, Object> hashOps;

    private KsetRedisTemplateOperations operations;
    private final KsetRedisTtlPolicy ttlPolicy = new KsetRedisTtlPolicy(Duration.ofMinutes(30), null);
    private final KsetRedisStreamSettings streamSettings =
            new KsetRedisStreamSettings(new com.kset.cloud.config.KsetRedisProperties());

    @BeforeEach
    void setUp() {
        when(template.opsForValue()).thenReturn(valueOps);
        operations = new KsetRedisTemplateOperations(template, ttlPolicy, streamSettings);
    }

    @Test
    void setAndGet() {
        when(valueOps.get("k")).thenReturn("v");
        operations.set("k", "v");
        verify(valueOps).set("k", "v", Duration.ofMinutes(30));
        assertEquals("v", operations.get("k", String.class));
    }

    @Test
    void setEx() {
        operations.setEx("k", "v", Duration.ofMinutes(1));
        verify(valueOps).set("k", "v", Duration.ofMinutes(1));
    }

    @Test
    void increment() {
        when(valueOps.increment("counter", 1L)).thenReturn(2L);
        when(template.expire(eq("counter"), any(Duration.class))).thenReturn(true);
        assertEquals(2L, operations.increment("counter"));
    }

    @Test
    void hSetAndHGet() {
        when(template.opsForHash()).thenReturn(hashOps);
        when(hashOps.get("h", "f")).thenReturn(1);
        operations.hSet("h", "f", 1);
        verify(hashOps).put("h", "f", 1);
        assertEquals(1, operations.hGet("h", "f", Integer.class));
    }

    @Test
    void multiGetReturnsMapAlignedWithKeys() {
        when(valueOps.multiGet(List.of("a", "b"))).thenReturn(Arrays.asList("1", null));
        Map<String, String> map = operations.multiGet(List.of("a", "b"), String.class);
        assertEquals("1", map.get("a"));
        assertEquals(null, map.get("b"));
    }

    @Test
    void deleteAllDelegatesToTemplate() {
        when(template.delete(List.of("k1", "k2"))).thenReturn(2L);
        assertEquals(2L, operations.deleteAll(List.of("k1", "k2")));
    }

    @Test
    void existsAll() {
        when(template.hasKey("k1")).thenReturn(true);
        when(template.hasKey("k2")).thenReturn(false);
        Map<String, Boolean> exists = operations.existsAll(List.of("k1", "k2"));
        assertTrue(exists.get("k1"));
        assertFalse(exists.get("k2"));
    }

    @Test
    void hMGet() {
        when(template.opsForHash()).thenReturn(hashOps);
        when(hashOps.multiGet(eq("user:1"), any())).thenReturn(List.of("alice", 18));
        Map<String, Object> fields = operations.hMGet("user:1", List.of("name", "age"), Object.class);
        assertEquals("alice", fields.get("name"));
        assertEquals(18, fields.get("age"));
    }

    @Test
    void hSetAll() {
        when(template.opsForHash()).thenReturn(hashOps);
        when(template.expire(eq("user:1"), any(Duration.class))).thenReturn(true);
        Map<String, Object> data = Map.of("name", "bob", "age", 20);
        operations.hSetAll("user:1", data);
        verify(hashOps).putAll(eq("user:1"), any(Map.class));
    }
}
