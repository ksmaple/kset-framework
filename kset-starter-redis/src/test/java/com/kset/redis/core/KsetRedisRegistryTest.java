package com.kset.redis.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KsetRedisRegistryTest {

    private KsetRedisRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new KsetRedisRegistry();
        KsetRedis.unbind();
    }

    @Test
    void registerPrimaryAndGet() {
        KsetRedisService primary = mockService("primary");
        registry.registerPrimary(primary);
        assertSame(primary, registry.primary());
        assertSame(primary, registry.get(KsetRedisRegistry.PRIMARY_NAME));
    }

    @Test
    void registerOverridesExisting() {
        KsetRedisService first = mockService("cache");
        KsetRedisService second = mockService("cache");
        registry.register("cache", first);
        registry.register("cache", second);
        assertSame(second, registry.get("cache"));
    }

    @Test
    void bindAllowsStaticAccess() {
        KsetRedisService primary = mockService("primary");
        registry.registerPrimary(primary);
        KsetRedis.bind(registry);
        assertSame(primary, KsetRedis.primary());
    }

    @Test
    void primaryBeforeInitThrows() {
        assertThrows(IllegalStateException.class, () -> registry.primary());
    }

    private static KsetRedisService mockService(String name) {
        KsetRedisService service = mock(KsetRedisService.class);
        when(service.getName()).thenReturn(name);
        when(service.sourceName()).thenReturn(name);
        return service;
    }
}
