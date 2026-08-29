package com.kset.common.lifecycle;

import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Objects;

/**
 * 健康状态摘流组件（{@link KsetLifecyclePhases#PHASE_READINESS}）：
 * 停机最先执行——将应用就绪状态置为 {@link ReadinessState#REFUSING_TRAFFIC}，
 * 使 Actuator readiness 探针立即失败，K8s 在 Dubbo/MQ 停收前先把本实例摘出流量。
 */
public class KsetReadinessLifecycle extends AbstractKsetLifecycleComponent {

    private final ApplicationEventPublisher eventPublisher;

    public KsetReadinessLifecycle(ApplicationEventPublisher eventPublisher) {
        super("kset-readiness", KsetLifecyclePhases.PHASE_READINESS);
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher must not be null");
    }

    @Override
    protected void doStop() {
        AvailabilityChangeEvent.publish(eventPublisher, this, ReadinessState.REFUSING_TRAFFIC);
        log.info("[kset-lifecycle] readiness state changed to REFUSING_TRAFFIC");
    }
}
