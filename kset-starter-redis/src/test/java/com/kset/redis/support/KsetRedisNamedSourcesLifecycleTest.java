package com.kset.redis.support;

import com.kset.redis.core.KsetRedisService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class KsetRedisNamedSourcesLifecycleTest {

    @Test
    void stopClosesNamedConnectionFactoriesOnce() {
        KsetRedisService service = mock(KsetRedisService.class);
        LettuceConnectionFactory connectionFactory = mock(LettuceConnectionFactory.class);
        KsetRedisNamedSources sources = new KsetRedisNamedSources(
                Map.of("cache", service),
                List.of(connectionFactory));

        sources.start();
        sources.stop();
        sources.destroy();

        verify(connectionFactory, times(1)).destroy();
    }

    @Test
    void phaseIsInfraSoFactoriesCloseLast() {
        KsetRedisNamedSources sources = KsetRedisNamedSources.empty();

        org.assertj.core.api.Assertions.assertThat(sources.getPhase())
                .isEqualTo(com.kset.common.lifecycle.KsetLifecyclePhases.PHASE_INFRA);
    }
}
