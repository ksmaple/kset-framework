package com.kset.redis.support;

import com.kset.redis.core.KsetRedisService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class KsetRedisNamedSourcesTest {

    @Test
    void destroyClosesNamedConnectionFactories() {
        KsetRedisService service = mock(KsetRedisService.class);
        LettuceConnectionFactory connectionFactory = mock(LettuceConnectionFactory.class);
        KsetRedisNamedSources sources = new KsetRedisNamedSources(
                Map.of("cache", service),
                List.of(connectionFactory));

        sources.destroy();

        verify(connectionFactory).destroy();
    }
}
