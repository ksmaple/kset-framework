package com.kset.cache;

import com.kset.cache.annotation.KsetCacheEvict;
import com.kset.cache.annotation.KsetCacheable;
import com.kset.cache.annotation.KsetCaching;
import com.kset.cache.autoconfigure.KsetCacheAutoConfiguration;
import com.kset.cache.core.KsetCacheLayer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class KsetCacheAspectTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withPropertyValues("kset.cache.default-layers=L1")
            .withUserConfiguration(TestCacheConfiguration.class)
            .withConfiguration(AutoConfigurations.of(AopAutoConfiguration.class, KsetCacheAutoConfiguration.class));

    @Test
    void cacheableUsesL1BeforeInvokingMethodAgain() {
        contextRunner.run(context -> {
            TestCacheService service = context.getBean(TestCacheService.class);

            assertThat(service.find("1")).isEqualTo("user-1-1");
            assertThat(service.find("1")).isEqualTo("user-1-1");

            assertThat(service.findCalls()).isEqualTo(1);
        });
    }

    @Test
    void cacheableCachesNullByDefault() {
        contextRunner.run(context -> {
            TestCacheService service = context.getBean(TestCacheService.class);

            assertThat(service.findNull("1")).isNull();
            assertThat(service.findNull("1")).isNull();

            assertThat(service.nullCalls()).isEqualTo(1);
        });
    }

    @Test
    void evictClearsL1() {
        contextRunner.run(context -> {
            TestCacheService service = context.getBean(TestCacheService.class);

            assertThat(service.find("2")).isEqualTo("user-2-1");
            service.evict("2");
            assertThat(service.find("2")).isEqualTo("user-2-2");

            assertThat(service.findCalls()).isEqualTo(2);
        });
    }

    @Test
    void cachingContainerWritesMultipleKeys() {
        contextRunner.run(context -> {
            TestCacheService service = context.getBean(TestCacheService.class);

            assertThat(service.findWithAlias("3")).isEqualTo("multi-3-1");
            assertThat(service.findAlias("3")).isEqualTo("multi-3-1");

            assertThat(service.multiCalls()).isEqualTo(1);
            assertThat(service.aliasCalls()).isZero();
        });
    }

    @Test
    void startupUsesL1ByDefault() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(AopAutoConfiguration.class, KsetCacheAutoConfiguration.class))
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void startupFailsWhenExplicitDefaultLayersRequireMissingL2() {
        new ApplicationContextRunner()
                .withPropertyValues("kset.cache.default-layers=L1,L2")
                .withConfiguration(AutoConfigurations.of(AopAutoConfiguration.class, KsetCacheAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("default-layers includes L2");
                });
    }

    static class TestCacheService {

        private final AtomicInteger findCalls = new AtomicInteger();
        private final AtomicInteger nullCalls = new AtomicInteger();
        private final AtomicInteger multiCalls = new AtomicInteger();
        private final AtomicInteger aliasCalls = new AtomicInteger();

        @KsetCacheable(cacheName = "user", key = "'user:' + #id", layers = {KsetCacheLayer.L1})
        public String find(String id) {
            return "user-" + id + "-" + findCalls.incrementAndGet();
        }

        @KsetCacheable(cacheName = "nullUser", key = "'user:' + #id", layers = {KsetCacheLayer.L1})
        public String findNull(String id) {
            nullCalls.incrementAndGet();
            return null;
        }

        @KsetCacheEvict(cacheName = "user", key = "'user:' + #id", layers = {KsetCacheLayer.L1})
        public void evict(String id) {
        }

        @KsetCaching(cacheable = {
                @KsetCacheable(cacheName = "multi", key = "'main:' + #id", layers = {KsetCacheLayer.L1}),
                @KsetCacheable(cacheName = "multi", key = "'alias:' + #id", layers = {KsetCacheLayer.L1})
        })
        public String findWithAlias(String id) {
            return "multi-" + id + "-" + multiCalls.incrementAndGet();
        }

        @KsetCacheable(cacheName = "multi", key = "'alias:' + #id", layers = {KsetCacheLayer.L1})
        public String findAlias(String id) {
            return "alias-" + id + "-" + aliasCalls.incrementAndGet();
        }

        int findCalls() {
            return findCalls.get();
        }

        int nullCalls() {
            return nullCalls.get();
        }

        int multiCalls() {
            return multiCalls.get();
        }

        int aliasCalls() {
            return aliasCalls.get();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class TestCacheConfiguration {

        @Bean
        TestCacheService testCacheService() {
            return new TestCacheService();
        }
    }
}
