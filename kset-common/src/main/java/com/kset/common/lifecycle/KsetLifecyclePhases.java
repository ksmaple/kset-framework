package com.kset.common.lifecycle;

/**
 * kset 统一启停相位。基于 Spring {@code SmartLifecycle}：启动按相位升序，停机按相位降序。
 *
 * <p>停机顺序：健康状态摘流（readiness 置为 REFUSING_TRAFFIC） → 流量入口（Dubbo 摘流/MQ 停收） → 事件执行器排空
 * → 业务线程池排空 → 监控指标 flush → 基础设施（Redis 连接）最后关闭。
 * 定时任务（@Scheduled）在 ContextClosedEvent 最先取消；HTTP/WebFlux 在途请求由 {@code server.shutdown=graceful}
 * 保证先于以上相位完成；DB 连接池在 Spring destroy 阶段最后关闭。
 */
public final class KsetLifecyclePhases {

    /** 基础设施（Redis 连接等），最后关闭。 */
    public static final int PHASE_INFRA = 100;

    /** 监控上报（指标 flush 与异步上报关闭）。 */
    public static final int PHASE_MONITOR = 200;

    /** 业务线程池（KsetThreadPoolFactory 全部业务池排空）。 */
    public static final int PHASE_THREAD_POOL = 300;

    /** 事件门面执行器（delay/async 排空）。 */
    public static final int PHASE_EVENT = 400;

    /** 流量入口（Dubbo 摘流/MQ 消费停收等），先于业务处理组件停止。 */
    public static final int PHASE_TRAFFIC = 500;

    /** 健康状态摘流（readiness 置为 REFUSING_TRAFFIC），全部组件中最先执行。 */
    public static final int PHASE_READINESS = 600;

    private KsetLifecyclePhases() {
    }
}
