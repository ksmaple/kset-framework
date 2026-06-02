package com.kset.auth;

import com.kset.auth.config.KsetAuthProperties;
import com.kset.auth.session.RedisLoginSessionStore;
import com.kset.common.auth.LoginUser;
import com.kset.redis.core.KsetRedisService;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisLoginSessionStoreTest {

    @Test
    void savesAndFindsSessionWithConfiguredKey() {
        KsetRedisService redis = mock(KsetRedisService.class);
        KsetAuthProperties properties = new KsetAuthProperties();
        RedisLoginSessionStore store = new RedisLoginSessionStore(redis, properties);
        LoginUser user = LoginUser.builder().userId("u1").build();

        store.save("t1", user, Duration.ofMinutes(10));

        verify(redis).setEx("kset:auth:session:t1", user.withToken("t1"), Duration.ofMinutes(10));
    }

    @Test
    void refreshesWhenTtlBelowThreshold() {
        KsetRedisService redis = mock(KsetRedisService.class);
        KsetAuthProperties properties = new KsetAuthProperties();
        RedisLoginSessionStore store = new RedisLoginSessionStore(redis, properties);
        LoginUser user = LoginUser.builder().userId("u1").build();
        when(redis.get("kset:auth:session:t1", LoginUser.class)).thenReturn(user);
        when(redis.ttl("kset:auth:session:t1")).thenReturn(60L);

        assertThat(store.findByToken("t1")).map(LoginUser::getUserId).contains("u1");

        verify(redis).expire("kset:auth:session:t1", Duration.ofHours(2));
    }
}
