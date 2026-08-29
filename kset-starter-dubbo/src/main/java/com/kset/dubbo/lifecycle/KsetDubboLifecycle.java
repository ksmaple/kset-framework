package com.kset.dubbo.lifecycle;

import com.kset.common.lifecycle.AbstractKsetLifecycleComponent;
import com.kset.common.lifecycle.KsetLifecyclePhases;
import org.apache.dubbo.config.bootstrap.DubboBootstrap;

/**
 * Dubbo 流量入口统一停机组件（{@link KsetLifecyclePhases#PHASE_TRAFFIC}）：
 * 停机时与 MQ 同相位优先停收——通过 {@link DubboBootstrap#stop()} 注销注册中心并取消服务暴露，
 * 等待时长由 {@code dubbo.application.shutwait}（默认 10000ms）控制。
 */
public class KsetDubboLifecycle extends AbstractKsetLifecycleComponent {

    public KsetDubboLifecycle() {
        super("kset-dubbo", KsetLifecyclePhases.PHASE_TRAFFIC);
    }

    @Override
    protected void doStop() {
        DubboBootstrap bootstrap = DubboBootstrap.getInstance();
        if (bootstrap.isStarted() && !bootstrap.isStopping() && !bootstrap.isStopped()) {
            bootstrap.stop();
            log.info("[kset-lifecycle] Dubbo services unexported and deregistered");
        }
    }
}
