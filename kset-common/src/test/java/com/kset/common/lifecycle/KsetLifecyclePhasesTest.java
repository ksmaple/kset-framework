package com.kset.common.lifecycle;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KsetLifecyclePhasesTest {

    @Test
    void shutdownOrderIsTrafficFirstInfraLast() {
        assertThat(KsetLifecyclePhases.PHASE_READINESS)
                .isGreaterThan(KsetLifecyclePhases.PHASE_TRAFFIC);
        assertThat(KsetLifecyclePhases.PHASE_TRAFFIC)
                .isGreaterThan(KsetLifecyclePhases.PHASE_EVENT);
        assertThat(KsetLifecyclePhases.PHASE_EVENT)
                .isGreaterThan(KsetLifecyclePhases.PHASE_THREAD_POOL);
        assertThat(KsetLifecyclePhases.PHASE_THREAD_POOL)
                .isGreaterThan(KsetLifecyclePhases.PHASE_MONITOR);
        assertThat(KsetLifecyclePhases.PHASE_MONITOR)
                .isGreaterThan(KsetLifecyclePhases.PHASE_INFRA);
    }
}
