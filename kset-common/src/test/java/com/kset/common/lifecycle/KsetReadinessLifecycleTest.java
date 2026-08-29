package com.kset.common.lifecycle;

import org.junit.jupiter.api.Test;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KsetReadinessLifecycleTest {

    @Test
    @SuppressWarnings("unchecked")
    void stopPublishesRefusingTraffic() {
        List<Object> events = new ArrayList<>();
        KsetReadinessLifecycle lifecycle = new KsetReadinessLifecycle(events::add);

        lifecycle.start();
        lifecycle.stop();

        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(AvailabilityChangeEvent.class);
        AvailabilityChangeEvent<ReadinessState> event =
                (AvailabilityChangeEvent<ReadinessState>) events.get(0);
        assertThat(event.getState()).isEqualTo(ReadinessState.REFUSING_TRAFFIC);
    }

    @Test
    void readinessPhaseStopsBeforeTraffic() {
        assertThat(KsetLifecyclePhases.PHASE_READINESS)
                .isGreaterThan(KsetLifecyclePhases.PHASE_TRAFFIC);
    }
}
