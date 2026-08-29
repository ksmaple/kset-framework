package com.kset.dubbo.lifecycle;

import com.kset.common.lifecycle.KsetLifecyclePhases;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class KsetDubboLifecycleTest {

    @Test
    void stopIsNoOpWhenDubboNotStarted() {
        KsetDubboLifecycle lifecycle = new KsetDubboLifecycle();

        lifecycle.start();
        assertThatCode(lifecycle::stop).doesNotThrowAnyException();

        assertThat(lifecycle.isRunning()).isFalse();
    }

    @Test
    void phaseIsTrafficSoDubboStopsFirst() {
        assertThat(new KsetDubboLifecycle().getPhase())
                .isEqualTo(KsetLifecyclePhases.PHASE_TRAFFIC);
    }
}
