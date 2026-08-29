package com.kset.mq.lifecycle;

import com.kset.common.lifecycle.AbstractKsetLifecycleComponent;
import com.kset.common.lifecycle.KsetLifecyclePhases;
import org.apache.rocketmq.client.support.RocketMQListenerContainer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.SmartLifecycle;

import java.util.Objects;

/**
 * MQ 流量入口统一停机组件（{@link KsetLifecyclePhases#PHASE_TRAFFIC}）：
 * 停机时最先停止 RocketMQ 监听容器，停收新消息，让在途消息在后续事件/线程池相位中排空。
 */
public class KsetMqEventLifecycle extends AbstractKsetLifecycleComponent {

    private final ObjectProvider<RocketMQListenerContainer> containers;

    public KsetMqEventLifecycle(ObjectProvider<RocketMQListenerContainer> containers) {
        super("kset-mq-event", KsetLifecyclePhases.PHASE_TRAFFIC);
        this.containers = Objects.requireNonNull(containers, "containers must not be null");
    }

    @Override
    protected void doStop() {
        containers.forEach(container -> {
            try {
                if (container instanceof SmartLifecycle lifecycle && lifecycle.isRunning()) {
                    lifecycle.stop();
                    log.info("[kset-lifecycle] RocketMQ listener container stopped");
                }
            } catch (RuntimeException e) {
                log.warn("[kset-lifecycle] failed to stop RocketMQ listener container", e);
            }
        });
    }
}
