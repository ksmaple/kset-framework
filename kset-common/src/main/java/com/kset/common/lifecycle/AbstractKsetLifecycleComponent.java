package com.kset.common.lifecycle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * kset 生命周期组件基类：统一承载名称、相位、运行标志与启停日志，子类实现 {@link #doStart()} 与 {@link #doStop()}。
 *
 * <p>{@link #shutdownLifecycle()} 幂等执行停机逻辑，即使组件从未 {@link #start()} 也生效，
 * 供 {@code DisposableBean.destroy()} 等非 SmartLifecycle 入口直接复用。</p>
 */
public abstract class AbstractKsetLifecycleComponent implements SmartLifecycle {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    private final String name;
    private final int phase;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean stopped = new AtomicBoolean(false);

    protected AbstractKsetLifecycleComponent(String name, int phase) {
        this.name = name;
        this.phase = phase;
    }

    public String getName() {
        return name;
    }

    @Override
    public final void start() {
        if (stopped.get()) {
            return;
        }
        if (running.compareAndSet(false, true)) {
            try {
                doStart();
                log.info("[kset-lifecycle] {} started (phase={})", name, phase);
            } catch (RuntimeException | Error e) {
                running.set(false);
                throw e;
            }
        }
    }

    @Override
    public final void stop() {
        if (running.get()) {
            shutdownLifecycle();
        }
    }

    /**
     * 幂等执行停机逻辑：仅首次调用生效，后续调用直接返回。
     */
    public final void shutdownLifecycle() {
        if (stopped.compareAndSet(false, true)) {
            running.set(false);
            doStop();
            log.info("[kset-lifecycle] {} stopped (phase={})", name, phase);
        }
    }

    @Override
    public final void stop(Runnable callback) {
        try {
            stop();
        } finally {
            callback.run();
        }
    }

    @Override
    public final boolean isRunning() {
        return running.get();
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public final int getPhase() {
        return phase;
    }

    protected void doStart() {
    }

    protected abstract void doStop();
}
