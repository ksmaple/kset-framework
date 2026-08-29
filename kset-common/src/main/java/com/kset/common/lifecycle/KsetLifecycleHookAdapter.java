package com.kset.common.lifecycle;

import java.util.Objects;

/**
 * {@link KsetLifecycleHook} 的 SmartLifecycle 适配器：业务侧注册本 Bean 即接入统一启停编排。
 *
 * <p>函数式用法（最简）：</p>
 * <pre>{@code
 * @Bean
 * KsetLifecycleHookAdapter localCacheShutdown() {
 *     return new KsetLifecycleHookAdapter(
 *             "local-cache", KsetLifecyclePhases.PHASE_EVENT + 10, localCache::shutdown);
 * }
 * }</pre>
 *
 * <p>接口式用法（逻辑结构化时）：实现 {@link KsetLifecycleHook} 后 {@code new KsetLifecycleHookAdapter(hook)}。</p>
 */
public class KsetLifecycleHookAdapter extends AbstractKsetLifecycleComponent {

    private final Runnable startAction;
    private final Runnable stopAction;

    public KsetLifecycleHookAdapter(KsetLifecycleHook hook) {
        this(Objects.requireNonNull(hook, "hook must not be null").getName(),
                hook.getPhase(), hook::onStart, hook::onStop);
    }

    public KsetLifecycleHookAdapter(String name, int phase, Runnable stopAction) {
        this(name, phase, null, stopAction);
    }

    public KsetLifecycleHookAdapter(String name, int phase, Runnable startAction, Runnable stopAction) {
        super(validateName(name), phase);
        this.startAction = startAction;
        this.stopAction = Objects.requireNonNull(stopAction, "stopAction must not be null");
    }

    @Override
    protected void doStart() {
        if (startAction != null) {
            startAction.run();
        }
    }

    @Override
    protected void doStop() {
        stopAction.run();
    }

    private static String validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        return name;
    }
}
