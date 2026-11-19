package com.kset.redis.cache;

import com.kset.cache.core.KsetCacheLayer;
import com.kset.cache.core.KsetCacheSpec;
import com.kset.cache.core.KsetCacheValue;
import com.kset.redis.core.KsetRedisService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;

class RedisKsetCacheStoreTest {

    @Test
    void delegatesToKsetRedisService() {
        KsetRedisService redisService = mock(KsetRedisService.class);
        RedisKsetCacheStore store = new RedisKsetCacheStore(redisService);
        KsetCacheSpec spec = new KsetCacheSpec("user", "1", List.of(KsetCacheLayer.L2),
                Duration.ofMinutes(5), Duration.ofMinutes(1), true, String.class);
        KsetCacheValue value = KsetCacheValue.of("alice");
        when(redisService.get(anyString(), eq(Long.class))).thenReturn(null);
        when(redisService.get("user::v0::1", KsetCacheValue.class)).thenReturn(value);

        Optional<KsetCacheValue> cached = store.get(spec);
        store.put(spec, value, Duration.ofMinutes(5));
        store.evict(spec);

        assertThat(cached).contains(value);
        verify(redisService).setEx("user::v0::1", value, Duration.ofMinutes(5));
        verify(redisService).delete("user::v0::1");
    }

    @Test
    void clearReplacesNamespaceWithExpiringGeneration() {
        KsetRedisService redisService = mock(KsetRedisService.class);
        RedisKsetCacheStore store = new RedisKsetCacheStore(redisService);

        store.clear("user");

        verify(redisService).setEx(startsWith("kset:cache:generation:"),
                argThat(value -> value instanceof Long generation && generation > 0),
                eq(Duration.ofDays(36_500)));
        verify(redisService, never()).scanKeys(anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void versionedPutKeepsTheSuppliedGeneration() {
        KsetRedisService redisService = mock(KsetRedisService.class);
        RedisKsetCacheStore store = new RedisKsetCacheStore(redisService);
        KsetCacheSpec spec = new KsetCacheSpec("user", "1", List.of(KsetCacheLayer.L2),
                Duration.ofMinutes(5), Duration.ofMinutes(1), true, String.class);
        KsetCacheValue value = KsetCacheValue.of("old");

        store.put(spec, value, Duration.ofMinutes(5), 12L);

        verify(redisService).setEx("user::v12::1", value, Duration.ofMinutes(5));
    }
}
