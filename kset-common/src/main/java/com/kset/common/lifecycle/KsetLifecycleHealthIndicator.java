package com.kset.common.lifecycle;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * kset 统一启停健康指标（{@code /actuator/health} 中的 {@code ksetLifecycle} 项）。
 *
 * <p>全部组件运行中时上报 UP；停机排空开始后（任一组件已停止）上报 OUT_OF_SERVICE，
 * 并携带各组件运行状态明细，便于定位排空进度。</p>
 */
public class KsetLifecycleHealthIndicator implements HealthIndicator {

    private final ObjectProvider<AbstractKsetLifecycleComponent> components;

    public KsetLifecycleHealthIndicator(ObjectProvider<AbstractKsetLifecycleComponent> components) {
        this.components = Objects.requireNonNull(components, "components must not be null");
    }

    @Override
    public Health health() {
        Map<String, String> details = new LinkedHashMap<>();
        boolean anyStopped = false;
        for (AbstractKsetLifecycleComponent component : components) {
            boolean running = component.isRunning();
            details.put(component.getName(), running ? "RUNNING" : "STOPPED");
            if (!running) {
                anyStopped = true;
            }
        }
        Health.Builder builder = anyStopped ? Health.outOfService() : Health.up();
        return builder.withDetail("components", details).build();
    }
}
