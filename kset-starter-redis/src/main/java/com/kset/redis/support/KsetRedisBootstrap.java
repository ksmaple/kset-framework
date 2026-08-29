package com.kset.redis.support;

import com.kset.redis.lock.KsetRedisLockExecutor;
import com.kset.redis.lock.KsetRedisLocks;
import com.kset.redis.core.KsetRedis;
import com.kset.redis.core.KsetRedisRegistry;
import com.kset.redis.core.KsetRedisService;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;

/**
 * 启动时注册 Redis 数据源并绑定静态门面。
 */
public class KsetRedisBootstrap implements InitializingBean, DisposableBean {

    private final KsetRedisRegistry registry;
    private final KsetRedisService primaryService;
    private final ObjectProvider<KsetRedisNamedSources> namedSources;
    private final ObjectProvider<KsetRedisLockExecutor> lockExecutor;

    public KsetRedisBootstrap(KsetRedisRegistry registry,
                              KsetRedisService primaryService,
                              ObjectProvider<KsetRedisNamedSources> namedSources,
                              ObjectProvider<KsetRedisLockExecutor> lockExecutor) {
        this.registry = registry;
        this.primaryService = primaryService;
        this.namedSources = namedSources;
        this.lockExecutor = lockExecutor;
    }

    @Override
    public void afterPropertiesSet() {
        registry.registerPrimary(primaryService);
        namedSources.ifAvailable(sources -> sources.registerAll(registry));
        KsetRedis.bind(registry);
        lockExecutor.ifAvailable(KsetRedisLocks::bind);
    }

    @Override
    public void destroy() {
        KsetRedisLocks.unbind();
        KsetRedis.unbind();
    }
}
