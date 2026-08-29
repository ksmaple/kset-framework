package com.kset.common.lifecycle;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KsetLifecycleHealthIndicatorTest {

    @Test
    void reportsUpWhenAllComponentsRunning() {
        FakeComponent component = new FakeComponent("fake", KsetLifecyclePhases.PHASE_EVENT);
        component.start();
        KsetLifecycleHealthIndicator indicator = new KsetLifecycleHealthIndicator(providerOf(component));

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails())
                .containsEntry("components", Map.of("fake", "RUNNING"));
    }

    @Test
    void reportsOutOfServiceWhenAnyComponentStopped() {
        FakeComponent running = new FakeComponent("running-biz", KsetLifecyclePhases.PHASE_EVENT);
        FakeComponent stopped = new FakeComponent("stopped-biz", KsetLifecyclePhases.PHASE_TRAFFIC);
        running.start();
        stopped.start();
        KsetLifecycleHealthIndicator indicator = new KsetLifecycleHealthIndicator(providerOf(running, stopped));

        stopped.stop();
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.OUT_OF_SERVICE);
        @SuppressWarnings("unchecked")
        Map<String, String> components = (Map<String, String>) health.getDetails().get("components");
        assertThat(components)
                .containsEntry("running-biz", "RUNNING")
                .containsEntry("stopped-biz", "STOPPED");
    }

    private static final class FakeComponent extends AbstractKsetLifecycleComponent {
        private FakeComponent(String name, int phase) {
            super(name, phase);
        }

        @Override
        protected void doStop() {
        }
    }

    @SafeVarargs
    private static <T> ObjectProvider<T> providerOf(T... items) {
        List<T> list = List.of(items);
        return new ObjectProvider<>() {
            @Override
            public T getObject() {
                return list.get(0);
            }

            @Override
            public Iterator<T> iterator() {
                return list.iterator();
            }
        };
    }
}
