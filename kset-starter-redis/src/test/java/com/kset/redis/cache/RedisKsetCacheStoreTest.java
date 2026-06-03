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

class RedisKsetCacheStoreTest {

    @Test
    void delegatesToKsetRedisService() {
        KsetRedisService redisService = mock(KsetRedisService.class);
        RedisKsetCacheStore store = new RedisKsetCacheStore(redisService);
        KsetCacheSpec spec = new KsetCacheSpec("user", "1", List.of(KsetCacheLayer.L2),
                Duration.ofMinutes(5), Duration.ofMinutes(1), true, String.class);
        KsetCacheValue value = KsetCacheValue.of("alice");
        when(redisService.get("user::1", KsetCacheValue.class)).thenReturn(value);

        Optional<KsetCacheValue> cached = store.get(spec);
        store.put(spec, value, Duration.ofMinutes(5));
        store.evict(spec);

        assertThat(cached).contains(value);
        verify(redisService).setEx("user::1", value, Duration.ofMinutes(5));
        verify(redisService).delete("user::1");
    }
}
