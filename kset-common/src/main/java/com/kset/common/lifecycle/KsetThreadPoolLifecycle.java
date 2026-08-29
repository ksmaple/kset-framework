package com.kset.common.lifecycle;

import com.kset.common.utils.thread.KsetThreadPoolFactory;

import java.time.Duration;
import java.util.Objects;

/**
 * 业务线程池统一停机组件（{@link KsetLifecyclePhases#PHASE_THREAD_POOL}）：
 * 停机时对 {@link KsetThreadPoolFactory} 全部业务池执行"排空 + 超时兜底"。
 */
public class KsetThreadPoolLifecycle extends AbstractKsetLifecycleComponent {

    private final KsetThreadPoolFactory factory;
    private final Duration shutdownTimeout;

    public KsetThreadPoolLifecycle(KsetThreadPoolFactory factory, Duration shutdownTimeout) {
        super("kset-thread-pool", KsetLifecyclePhases.PHASE_THREAD_POOL);
        this.factory = Objects.requireNonNull(factory, "factory must not be null");
        this.shutdownTimeout = Objects.requireNonNull(shutdownTimeout, "shutdownTimeout must not be null");
    }

    @Override
    protected void doStop() {
        log.info("[kset-lifecycle] draining {} thread pool(s), shutdownTimeout={}ms",
                factory.getAllMetrics().size(), shutdownTimeout.toMillis());
        factory.shutdownAll(shutdownTimeout.toMillis());
    }
}
