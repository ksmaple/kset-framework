package com.kset.redis.autoconfigure;

import com.kset.redis.core.KsetRedisService;
import com.kset.redis.codec.KsetFastjsonRedisSerializer;
import com.kset.redis.config.KsetRedisSerializerConfiguration;
import com.kset.redis.lock.KsetRedisLockExecutor;
import com.kset.redis.lock.internal.KsetRedissonLockProvider;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class KsetRedisAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RedisAutoConfiguration.class, KsetRedisAutoConfiguration.class))
            .withBean(RedisConnectionFactory.class, () -> mock(RedisConnectionFactory.class));

    private final ApplicationContextRunner contextRunnerWithRedissonClient = contextRunner
            .withBean(RedissonClient.class, () -> mock(RedissonClient.class));

    @Test
    void keepsNativeRedisBeansAndAddsKsetRedisTemplate() {
        contextRunnerWithRedissonClient.run(context -> {
            assertThat(context).hasBean("redisTemplate");
            assertThat(context).hasBean("stringRedisTemplate");
            assertThat(context).hasBean("ksetRedisTemplate");
            assertThat(context).hasBean(KsetRedisSerializerConfiguration.BEAN_NAME);
            assertThat(context).hasSingleBean(StringRedisTemplate.class);
            assertThat(context).hasSingleBean(KsetRedisService.class);
            assertThat(context).hasSingleBean(RedissonClient.class);
            assertThat(context).hasSingleBean(KsetRedissonLockProvider.class);
            assertThat(context).hasSingleBean(KsetRedisLockExecutor.class);
            assertThat(context.getBean(KsetRedisService.class).template())
                    .isSameAs(context.getBean("ksetRedisTemplate", RedisTemplate.class));
            assertThat(context.getBean("redisTemplate"))
                    .isNotSameAs(context.getBean("ksetRedisTemplate"));
        });
    }

    @Test
    void allowsOverridingKsetRedisValueSerializer() {
        KsetFastjsonRedisSerializer customSerializer = new KsetFastjsonRedisSerializer();

        contextRunnerWithRedissonClient
                .withBean(KsetRedisSerializerConfiguration.BEAN_NAME,
                        KsetFastjsonRedisSerializer.class,
                        () -> customSerializer)
                .run(context -> assertThat(context.getBean(KsetRedisSerializerConfiguration.BEAN_NAME))
                        .isSameAs(customSerializer));
    }

    @Test
    void allowsDisablingRedissonLockBeans() {
        contextRunner
                .withPropertyValues("kset.redis.redisson.enabled=false")
                .run(context -> {
                    assertThat(context).hasBean("ksetRedisTemplate");
                    assertThat(context).hasSingleBean(KsetRedisService.class);
                    assertThat(context).doesNotHaveBean(RedissonClient.class);
                    assertThat(context).doesNotHaveBean(KsetRedissonLockProvider.class);
                    assertThat(context).doesNotHaveBean(KsetRedisLockExecutor.class);
                });
    }
}
