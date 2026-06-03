package com.kset.cache;

import com.kset.cache.annotation.KsetCacheConfig;
import com.kset.cache.annotation.KsetCacheEvict;
import com.kset.cache.annotation.KsetCacheable;
import com.kset.cache.annotation.KsetCaching;
import com.kset.cache.autoconfigure.KsetCacheAutoConfiguration;
import com.kset.cache.config.KsetCacheProperties;
import com.kset.cache.core.KsetCacheLayer;
import com.kset.cache.core.KsetCacheSpec;
import com.kset.cache.core.KsetCacheStore;
import com.kset.cache.core.KsetCacheValue;
import com.kset.cache.interceptor.KsetCacheKeyGenerator;
import com.kset.cache.store.CaffeineKsetCacheStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
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
    void cachingContainerSupportsDifferentCacheNamesAndKeys() {
        contextRunner.run(context -> {
            TestCacheService service = context.getBean(TestCacheService.class);

            assertThat(service.findAcrossCaches("20")).isEqualTo("cross-20-1");
            assertThat(service.findCrossMain("20")).isEqualTo("cross-20-1");
            assertThat(service.findCrossAlias("20")).isEqualTo("cross-20-1");

            service.evictAcrossCaches("20");
            assertThat(service.findCrossMain("20")).isEqualTo("main-20-1");
            assertThat(service.findCrossAlias("20")).isEqualTo("alias-20-1");

            assertThat(service.crossCalls()).isEqualTo(1);
            assertThat(service.crossMainCalls()).isEqualTo(1);
            assertThat(service.crossAliasCalls()).isEqualTo(1);
        });
    }

    @Test
    void cacheNamesAliasAndDefaultKeyAreSupported() {
        contextRunner.run(context -> {
            TestCacheService service = context.getBean(TestCacheService.class);

            assertThat(service.findByAlias("4")).isEqualTo("alias-4-1");
            assertThat(service.findByAlias("4")).isEqualTo("alias-4-1");

            assertThat(service.aliasNameCalls()).isEqualTo(1);
        });
    }

    @Test
    void cacheConfigProvidesDefaultCacheNameAndKeyGenerator() {
        contextRunner.run(context -> {
            TestCacheService service = context.getBean(TestCacheService.class);

            assertThat(service.findWithConfig("12")).isEqualTo("config-12-1");
            assertThat(service.findWithConfig("12")).isEqualTo("config-12-1");
            service.clearConfig("12");
            assertThat(service.findWithConfig("12")).isEqualTo("config-12-2");

            assertThat(service.configCalls()).isEqualTo(2);
        });
    }

    @Test
    void conditionAndUnlessControlCacheWrites() {
        contextRunner.run(context -> {
            TestCacheService service = context.getBean(TestCacheService.class);

            assertThat(service.findWhenEnabled("5", false)).isEqualTo("condition-5-1");
            assertThat(service.findWhenEnabled("5", false)).isEqualTo("condition-5-2");
            assertThat(service.findWhenEnabled("5", true)).isEqualTo("condition-5-3");
            assertThat(service.findWhenEnabled("5", true)).isEqualTo("condition-5-3");

            assertThat(service.findUnless("6", true)).isEqualTo("unless-6-1");
            assertThat(service.findUnless("6", true)).isEqualTo("unless-6-2");
            assertThat(service.findUnless("6", false)).isEqualTo("unless-6-3");
            assertThat(service.findUnless("6", false)).isEqualTo("unless-6-3");
        });
    }

    @Test
    void allEntriesClearsKsetCacheNamespace() {
        contextRunner.run(context -> {
            TestCacheService service = context.getBean(TestCacheService.class);

            assertThat(service.find("7")).isEqualTo("user-7-1");
            assertThat(service.find("8")).isEqualTo("user-8-2");
            service.clearUsers();
            assertThat(service.find("7")).isEqualTo("user-7-3");
            assertThat(service.find("8")).isEqualTo("user-8-4");
        });
    }

    @Test
    void allEntriesFallbacksToL1WhenAnnotationLayerIsMissing() {
        contextRunner.run(context -> {
            TestCacheService service = context.getBean(TestCacheService.class);

            assertThat(service.find("31")).isEqualTo("user-31-1");
            service.clearUsersWithL2Only();
            assertThat(service.find("31")).isEqualTo("user-31-2");
            assertThat(service.findCalls()).isEqualTo(2);
        });
    }

    @Test
    void allEntriesUsesAnnotationLayersWhenLayerExists() {
        new ApplicationContextRunner()
                .withPropertyValues("kset.cache.default-layers=L1")
                .withUserConfiguration(TestCacheConfiguration.class, TestL2StoreConfiguration.class)
                .withConfiguration(AutoConfigurations.of(AopAutoConfiguration.class, KsetCacheAutoConfiguration.class))
                .run(context -> {
                    TestCacheService service = context.getBean(TestCacheService.class);

                    assertThat(service.find("33")).isEqualTo("user-33-1");
                    service.clearUsersWithL2Only();
                    assertThat(service.find("33")).isEqualTo("user-33-1");
                    assertThat(service.findCalls()).isEqualTo(1);
                });
    }

    @Test
    void unlessCacheableStillUsesSingleFlight() throws Exception {
        contextRunner.run(context -> {
            TestCacheService service = context.getBean(TestCacheService.class);

            assertThat(service.findUnless("32", false)).isEqualTo("unless-32-1");
            assertThat(service.findUnless("32", false)).isEqualTo("unless-32-1");

            assertThat(service.unlessCalls()).isEqualTo(1);
        });
    }

    @Test
    void startupUsesL1ByDefault() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(AopAutoConfiguration.class, KsetCacheAutoConfiguration.class))
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void startupUsesCaffeineAsDefaultL1Store() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(AopAutoConfiguration.class, KsetCacheAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean("ksetCaffeineCacheStore", KsetCacheStore.class))
                            .isInstanceOf(CaffeineKsetCacheStore.class);
                });
    }

    @Test
    void l1CaffeineOptionsAreConfigurable() {
        new ApplicationContextRunner()
                .withPropertyValues(
                        "kset.cache.l1.initial-capacity=64",
                        "kset.cache.l1.record-stats=true",
                        "kset.cache.ttl-jitter-enabled=true",
                        "kset.cache.ttl-jitter-percent=20")
                .withConfiguration(AutoConfigurations.of(AopAutoConfiguration.class, KsetCacheAutoConfiguration.class))
                .run(context -> {
                    KsetCacheProperties properties = context.getBean(KsetCacheProperties.class);
                    assertThat(properties.getL1().getInitialCapacity()).isEqualTo(64);
                    assertThat(properties.getL1().isRecordStats()).isTrue();
                    assertThat(properties.isTtlJitterEnabled()).isTrue();
                    assertThat(properties.getTtlJitterPercent()).isEqualTo(20);
                });
    }

    @Test
    void emptyDefaultLayersFallbackToL1() {
        KsetCacheProperties properties = new KsetCacheProperties();
        properties.setDefaultLayers(java.util.List.of());

        assertThat(properties.getDefaultLayers()).containsExactly(KsetCacheLayer.L1);
    }

    @Test
    void startupFallsBackToL1WhenExplicitDefaultLayersRequireMissingL2() {
        new ApplicationContextRunner()
                .withPropertyValues("kset.cache.default-layers=L1,L2")
                .withUserConfiguration(TestCacheConfiguration.class)
                .withConfiguration(AutoConfigurations.of(AopAutoConfiguration.class, KsetCacheAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();

                    TestCacheService service = context.getBean(TestCacheService.class);
                    assertThat(service.find("30")).isEqualTo("user-30-1");
                    assertThat(service.find("30")).isEqualTo("user-30-1");
                });
    }

    @Test
    void l2CanRunWithoutL1() {
        new ApplicationContextRunner()
                .withPropertyValues("kset.cache.l1.enabled=false", "kset.cache.default-layers=L2")
                .withUserConfiguration(TestCacheConfiguration.class, TestL2StoreConfiguration.class)
                .withConfiguration(AutoConfigurations.of(AopAutoConfiguration.class, KsetCacheAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean("ksetCaffeineCacheStore");

                    TestCacheService service = context.getBean(TestCacheService.class);
                    assertThat(service.findByAlias("34")).isEqualTo("alias-34-1");
                    assertThat(service.findByAlias("34")).isEqualTo("alias-34-1");
                    assertThat(service.aliasNameCalls()).isEqualTo(1);
                });
    }

    @Test
    void cacheConfigCanSelectL2WithoutL1() {
        new ApplicationContextRunner()
                .withPropertyValues("kset.cache.l1.enabled=false", "kset.cache.default-layers=L1")
                .withUserConfiguration(L2OnlyCacheConfiguration.class, TestL2StoreConfiguration.class)
                .withConfiguration(AutoConfigurations.of(AopAutoConfiguration.class, KsetCacheAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean("ksetCaffeineCacheStore");

                    L2OnlyCacheService service = context.getBean(L2OnlyCacheService.class);
                    assertThat(service.find("35")).isEqualTo("l2-35-1");
                    assertThat(service.find("35")).isEqualTo("l2-35-1");
                    assertThat(service.calls()).isEqualTo(1);
                });
    }

    @KsetCacheConfig(cacheNames = "configUser", keyGenerator = "testKeyGenerator")
    static class TestCacheService {

        private final AtomicInteger findCalls = new AtomicInteger();
        private final AtomicInteger nullCalls = new AtomicInteger();
        private final AtomicInteger multiCalls = new AtomicInteger();
        private final AtomicInteger aliasCalls = new AtomicInteger();
        private final AtomicInteger crossCalls = new AtomicInteger();
        private final AtomicInteger crossMainCalls = new AtomicInteger();
        private final AtomicInteger crossAliasCalls = new AtomicInteger();
        private final AtomicInteger aliasNameCalls = new AtomicInteger();
        private final AtomicInteger conditionCalls = new AtomicInteger();
        private final AtomicInteger unlessCalls = new AtomicInteger();
        private final AtomicInteger configCalls = new AtomicInteger();

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

        @KsetCaching(cacheable = {
                @KsetCacheable(cacheName = "crossMain", key = "'main:' + #id", layers = {KsetCacheLayer.L1}),
                @KsetCacheable(cacheName = "crossAlias", key = "'alias:' + #id", layers = {KsetCacheLayer.L1})
        })
        public String findAcrossCaches(String id) {
            return "cross-" + id + "-" + crossCalls.incrementAndGet();
        }

        @KsetCacheable(cacheName = "crossMain", key = "'main:' + #id", layers = {KsetCacheLayer.L1})
        public String findCrossMain(String id) {
            return "main-" + id + "-" + crossMainCalls.incrementAndGet();
        }

        @KsetCacheable(cacheName = "crossAlias", key = "'alias:' + #id", layers = {KsetCacheLayer.L1})
        public String findCrossAlias(String id) {
            return "alias-" + id + "-" + crossAliasCalls.incrementAndGet();
        }

        @KsetCaching(evict = {
                @KsetCacheEvict(cacheName = "crossMain", key = "'main:' + #id", layers = {KsetCacheLayer.L1}),
                @KsetCacheEvict(cacheName = "crossAlias", key = "'alias:' + #id", layers = {KsetCacheLayer.L1})
        })
        public void evictAcrossCaches(String id) {
        }

        @KsetCacheable("aliasName")
        public String findByAlias(String id) {
            return "alias-" + id + "-" + aliasNameCalls.incrementAndGet();
        }

        @KsetCacheable(cacheName = "condition", key = "'condition:' + #id", condition = "#enabled", layers = {KsetCacheLayer.L1})
        public String findWhenEnabled(String id, boolean enabled) {
            return "condition-" + id + "-" + conditionCalls.incrementAndGet();
        }

        @KsetCacheable(cacheName = "unless", key = "'unless:' + #id", unless = "#skip", layers = {KsetCacheLayer.L1})
        public String findUnless(String id, boolean skip) {
            return "unless-" + id + "-" + unlessCalls.incrementAndGet();
        }

        @KsetCacheEvict(cacheName = "user", allEntries = true, layers = {KsetCacheLayer.L1})
        public void clearUsers() {
        }

        @KsetCacheEvict(cacheName = "user", allEntries = true, layers = {KsetCacheLayer.L2})
        public void clearUsersWithL2Only() {
        }

        @KsetCacheable
        public String findWithConfig(String id) {
            return "config-" + id + "-" + configCalls.incrementAndGet();
        }

        @KsetCacheEvict
        public void clearConfig(String id) {
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

        int crossCalls() {
            return crossCalls.get();
        }

        int crossMainCalls() {
            return crossMainCalls.get();
        }

        int crossAliasCalls() {
            return crossAliasCalls.get();
        }

        int aliasNameCalls() {
            return aliasNameCalls.get();
        }

        int unlessCalls() {
            return unlessCalls.get();
        }

        int configCalls() {
            return configCalls.get();
        }

    }

    @Configuration(proxyBeanMethods = false)
    static class TestCacheConfiguration {

        @Bean
        TestCacheService testCacheService() {
            return new TestCacheService();
        }

        @Bean
        KsetCacheKeyGenerator testKeyGenerator() {
            return (target, method, args) -> "configured:" + args[0];
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class TestL2StoreConfiguration {

        @Bean
        KsetCacheStore testL2CacheStore() {
            return new KsetCacheStore() {
                private final Map<String, KsetCacheValue> cache = new ConcurrentHashMap<>();

                @Override
                public KsetCacheLayer layer() {
                    return KsetCacheLayer.L2;
                }

                @Override
                public Optional<KsetCacheValue> get(KsetCacheSpec spec) {
                    return Optional.ofNullable(cache.get(spec.fullKey()));
                }

                @Override
                public void put(KsetCacheSpec spec, KsetCacheValue value, Duration ttl) {
                    cache.put(spec.fullKey(), value);
                }

                @Override
                public void evict(KsetCacheSpec spec) {
                    cache.remove(spec.fullKey());
                }

                @Override
                public void clear(String cacheName) {
                    cache.keySet().removeIf(key -> key.startsWith(cacheName + "::"));
                }
            };
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class L2OnlyCacheConfiguration {

        @Bean
        L2OnlyCacheService l2OnlyCacheService() {
            return new L2OnlyCacheService();
        }
    }

    @KsetCacheConfig(cacheNames = "l2Only", layers = {KsetCacheLayer.L2})
    static class L2OnlyCacheService {

        private final AtomicInteger calls = new AtomicInteger();

        @KsetCacheable
        public String find(String id) {
            return "l2-" + id + "-" + calls.incrementAndGet();
        }

        int calls() {
            return calls.get();
        }
    }

}
