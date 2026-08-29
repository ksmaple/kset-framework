package com.kset.common.lifecycle;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KsetLifecycleHookAdapterTest {

    @Test
    void delegatesStartAndStopToHook() {
        AtomicInteger starts = new AtomicInteger();
        AtomicInteger stops = new AtomicInteger();
        KsetLifecycleHook hook = new KsetLifecycleHook() {
            @Override
            public String getName() {
                return "biz-hook";
            }

            @Override
            public int getPhase() {
                return KsetLifecyclePhases.PHASE_EVENT + 10;
            }

            @Override
            public void onStart() {
                starts.incrementAndGet();
            }

            @Override
            public void onStop() {
                stops.incrementAndGet();
            }
        };
        KsetLifecycleHookAdapter adapter = new KsetLifecycleHookAdapter(hook);

        adapter.start();
        adapter.stop();

        assertThat(starts.get()).isEqualTo(1);
        assertThat(stops.get()).isEqualTo(1);
        assertThat(adapter.getName()).isEqualTo("biz-hook");
        assertThat(adapter.getPhase()).isEqualTo(KsetLifecyclePhases.PHASE_EVENT + 10);
    }

    @Test
    void functionalStyleRunsStopAction() {
        AtomicInteger stops = new AtomicInteger();
        KsetLifecycleHookAdapter adapter = new KsetLifecycleHookAdapter(
                "functional-hook", KsetLifecyclePhases.PHASE_MONITOR, stops::incrementAndGet);

        adapter.start();
        adapter.stop();
        adapter.stop();

        assertThat(stops.get()).isEqualTo(1);
    }

    @Test
    void rejectsBlankNameAndNullStopAction() {
        assertThatThrownBy(() -> new KsetLifecycleHookAdapter(" ", 0, () -> { }))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new KsetLifecycleHookAdapter("x", 0, null))
                .isInstanceOf(NullPointerException.class);
    }
}
