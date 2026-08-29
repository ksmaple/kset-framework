package com.kset.redis.support;

import com.kset.common.lifecycle.AbstractKsetLifecycleComponent;
import com.kset.common.lifecycle.KsetLifecyclePhases;
import com.kset.redis.core.KsetRedisRegistry;
import com.kset.redis.core.KsetRedisService;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 命名 Redis 数据源集合（由多源自动配置产出）。
 *
 * <p>停机接入 kset 统一启停（{@link KsetLifecyclePhases#PHASE_INFRA}）：作为基础设施最后关闭连接工厂。
 */
public class KsetRedisNamedSources extends AbstractKsetLifecycleComponent implements DisposableBean {

    private final Map<String, KsetRedisService> services;
    private final List<LettuceConnectionFactory> connectionFactories;

    public KsetRedisNamedSources(Map<String, KsetRedisService> services) {
        this(services, Collections.emptyList());
    }

    public KsetRedisNamedSources(Map<String, KsetRedisService> services,
                                 List<LettuceConnectionFactory> connectionFactories) {
        super("kset-redis-named-sources", KsetLifecyclePhases.PHASE_INFRA);
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
    protected void doStop() {
        connectionFactories.forEach(LettuceConnectionFactory::destroy);
    }

    @Override
    public void destroy() {
        shutdownLifecycle();
    }

    /**
     * 保留原因（feature-key=graceful-lifecycle, change-id=graceful-lifecycle-v1）：原 DisposableBean 硬关实现，
     * 未接入统一启停时用于恢复原行为。
     */
    @SuppressWarnings("unused")
    public void destroyForRollback() {
        connectionFactories.forEach(LettuceConnectionFactory::destroy);
    }
}
