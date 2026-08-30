package com.kset.redis.core;

import com.kset.redis.config.KsetRedisProperties;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 多 Redis 数据源注册中心；支持手动 {@link #register(String, KsetRedisService)} 覆盖自动配置。
 */
public class KsetRedisRegistry {

    public static final String PRIMARY_NAME = KsetRedisProperties.PRIMARY_SOURCE_NAME;

    private final ConcurrentHashMap<String, KsetRedisService> services = new ConcurrentHashMap<>();
    private volatile KsetRedisService primary;

    public void registerPrimary(KsetRedisService service) {
        if (service == null) {
            throw new IllegalArgumentException("primary KsetRedisService must not be null");
        }
        this.primary = service;
        services.put(PRIMARY_NAME, service);
    }

    public void register(String name, KsetRedisService service) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Redis source name must not be blank");
        }
        if (service == null) {
            throw new IllegalArgumentException("KsetRedisService must not be null for source: " + name);
        }
        services.put(name, service);
    }

    public KsetRedisService primary() {
        KsetRedisService p = primary;
        if (p == null) {
            throw new IllegalStateException(
                    "KsetRedisRegistry is not initialized; ensure kset-starter-redis is on the classpath "
                            + "and kset.redis.enabled=true");
        }
        return p;
    }

    public KsetRedisService get(String name) {
        if (PRIMARY_NAME.equals(name)) {
            return primary();
        }
        KsetRedisService service = services.get(name);
        if (service == null) {
            throw new IllegalStateException("No KsetRedisService registered for source: " + name);
        }
        return service;
    }

    public Optional<KsetRedisService> find(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        if (PRIMARY_NAME.equals(name)) {
            return Optional.ofNullable(primary);
        }
        return Optional.ofNullable(services.get(name));
    }

    public Map<String, KsetRedisService> snapshot() {
        return Map.copyOf(services);
    }
}
