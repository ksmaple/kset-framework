package com.kset.common.lifecycle;

/**
 * 业务侧接入 kset 统一启停的扩展契约：实现本接口并用 {@link KsetLifecycleHookAdapter} 注册为 Bean，
 * 即可按 {@link #getPhase()} 进入统一启停编排（启动升序、停机降序），并自动出现在
 * {@code /actuator/health} 的 {@code ksetLifecycle} 健康明细中。
 *
 * <p>相位直接使用 {@link KsetLifecyclePhases} 常量或加减偏移插入两个内置相位之间，
 * 例如 {@code KsetLifecyclePhases.PHASE_TRAFFIC + 10} 表示紧随 MQ/Dubbo 停收之后停止。</p>
 */
public interface KsetLifecycleHook {

    /**
     * 组件名称，用于启停日志与健康明细展示。
     */
    String getName();

    /**
     * 启停相位：启动按升序、停机按降序；参考 {@link KsetLifecyclePhases}。
     */
    int getPhase();

    /**
     * 启动回调（容器刷新阶段按相位升序调用），默认无操作。
     */
    default void onStart() {
    }

    /**
     * 停机回调（容器关闭阶段按相位降序调用），实现内应完成停收、排空与资源释放。
     */
    void onStop();
}
