package com.kset.common.monitor.reporter;

/**
 * 上报执行器兼容 SPI。
 *
 * <p>门面层默认同步调用后端；异步策略由具体后端或外部监控框架决定。</p>
 */
public interface AsyncReporter {

    void report(Runnable task);

    void shutdown();
}
