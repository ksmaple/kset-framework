package com.kset.redis.support;

import com.kset.redis.core.KsetRedisRegistry;
import com.kset.redis.core.KsetRedisService;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 命名 Redis 数据源集合（由多源自动配置产出）。
 */
public class KsetRedisNamedSources implements DisposableBean {

    private final Map<String, KsetRedisService> services;
    private final List<LettuceConnectionFactory> connectionFactories;

    public KsetRedisNamedSources(Map<String, KsetRedisService> services) {
        this(services, Collections.emptyList());
    }

    public KsetRedisNamedSources(Map<String, KsetRedisService> services,
                                 List<LettuceConnectionFactory> connectionFactories) {
        this.services = services != null ? Map.copyOf(services) : Map.of();
        this.connectionFactories = connectionFactories != null ? List.copyOf(connectionFactories) : List.of();
    }

    public static KsetRedisNamedSources empty() {
        return new KsetRedisNamedSources(Collections.emptyMap());
    }

    public Map<String, KsetRedisService> getServices() {
        return services;
    }

    public void registerAll(KsetRedisRegistry registry) {
        services.forEach(registry::register);
    }

    @Override
    public void destroy() {
        connectionFactories.forEach(LettuceConnectionFactory::destroy);
    }
}
