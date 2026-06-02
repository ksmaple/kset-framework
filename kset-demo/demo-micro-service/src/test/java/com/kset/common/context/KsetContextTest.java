package com.kset.common.context;

import com.kset.common.auth.LoginUser;
import com.kset.common.monitor.Monitor;
import com.kset.common.trace.TraceHeaders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KsetContextTest {

    @AfterEach
    void clear() {
        KsetContext.clear();
        Monitor.clear();
    }

    @Test
    void putsGetsRemovesAndClearsValues() {
        LoginUser user = LoginUser.builder().userId("u1").build();

        KsetContext.put(KsetContextKeys.LOGIN_USER, user);

        assertThat(KsetContext.get(KsetContextKeys.LOGIN_USER)).contains(user);
        KsetContext.remove(KsetContextKeys.LOGIN_USER);
        assertThat(KsetContext.get(KsetContextKeys.LOGIN_USER)).isEmpty();

        KsetContext.put(KsetContextKeys.LANGUAGE, "zh-CN");
        KsetContext.clear();
        assertThat(KsetContext.get(KsetContextKeys.LANGUAGE)).isEmpty();
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void rejectsWrongValueType() {
        KsetContextKey rawKey = KsetContextKeys.LOGIN_USER;

        assertThatThrownBy(() -> KsetContext.put(rawKey, "bad"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void snapshotIsIndependentFromCurrentContext() {
        KsetContext.put(KsetContextKeys.LANGUAGE, "zh-CN");
        KsetContextSnapshot snapshot = KsetContext.capture();

        KsetContext.put(KsetContextKeys.LANGUAGE, "en-US");

        assertThat(snapshot.get(KsetContextKeys.LANGUAGE)).contains("zh-CN");
        assertThat(KsetContext.get(KsetContextKeys.LANGUAGE)).contains("en-US");
    }

    @Test
    void scopeRestoresPreviousContext() {
        KsetContext.put(KsetContextKeys.LANGUAGE, "zh-CN");
        KsetContextSnapshot snapshot = new KsetContextSnapshot(java.util.Map.of(
                KsetContextKeys.LANGUAGE.getName(), "en-US"));

        try (KsetContextScope ignored = KsetContext.openScope(snapshot)) {
            assertThat(KsetContext.get(KsetContextKeys.LANGUAGE)).contains("en-US");
        }

        assertThat(KsetContext.get(KsetContextKeys.LANGUAGE)).contains("zh-CN");
    }

    @Test
    void nestedScopeRestoresInOrder() {
        KsetContext.put(KsetContextKeys.LANGUAGE, "root");
        KsetContextSnapshot first = new KsetContextSnapshot(java.util.Map.of(KsetContextKeys.LANGUAGE.getName(), "first"));
        KsetContextSnapshot second = new KsetContextSnapshot(java.util.Map.of(KsetContextKeys.LANGUAGE.getName(), "second"));

        try (KsetContextScope ignored = KsetContext.openScope(first)) {
            try (KsetContextScope ignored2 = KsetContext.openScope(second)) {
                assertThat(KsetContext.get(KsetContextKeys.LANGUAGE)).contains("second");
            }
            assertThat(KsetContext.get(KsetContextKeys.LANGUAGE)).contains("first");
        }

        assertThat(KsetContext.get(KsetContextKeys.LANGUAGE)).contains("root");
    }

    @Test
    void traceContextSynchronizesMdc() {
        KsetContext.put(KsetContextKeys.TRACE_ID, "trace-1");
        KsetContext.put(KsetContextKeys.SPAN_ID, "span-1");

        assertThat(MDC.get(TraceHeaders.TRACE_ID_KEY)).isEqualTo("trace-1");
        assertThat(MDC.get(TraceHeaders.SPAN_ID_KEY)).isEqualTo("span-1");

        KsetContext.restore(new KsetContextSnapshot(java.util.Map.of(KsetContextKeys.TRACE_ID.getName(), "trace-2")));

        assertThat(MDC.get(TraceHeaders.TRACE_ID_KEY)).isEqualTo("trace-2");
        assertThat(MDC.get(TraceHeaders.SPAN_ID_KEY)).isNull();
        assertThat(Monitor.currentTraceId()).contains("trace-2");
        assertThat(Optional.ofNullable(MDC.get(TraceHeaders.GRAY_TAG_KEY))).isEmpty();
    }

    @Test
    void namespacedKeysAvoidBusinessCollision() {
        KsetContextKey<String> orderId = KsetContextKey.of("order", "currentId", String.class);
        KsetContextKey<String> cmsId = KsetContextKey.of("cms", "currentId", String.class);

        KsetContext.put(orderId, "order-1");
        KsetContext.put(cmsId, "cms-1");

        assertThat(KsetContext.get(orderId)).contains("order-1");
        assertThat(KsetContext.get(cmsId)).contains("cms-1");
        assertThat(orderId.getName()).isEqualTo("order.currentId");
    }

    @Test
    void rejectsSameNameWithDifferentMetadata() {
        KsetContextKey<String> propagatable = KsetContextKey.of("biz.secret", String.class, true, true);
        KsetContextKey<String> localOnly = KsetContextKey.of("biz.secret", String.class, false, true);

        KsetContext.register(propagatable);

        assertThatThrownBy(() -> KsetContext.register(localOnly))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("metadata conflict");
    }

    @Test
    void excludesNonPropagatableKeysFromSnapshot() {
        KsetContextKey<String> localOnly = KsetContextKey.of("job.localOnly", String.class, false, false);
        KsetContext.put(localOnly, "local");
        KsetContext.put(KsetContextKeys.LANGUAGE, "zh-CN");

        KsetContextSnapshot snapshot = KsetContext.capture();

        assertThat(snapshot.get(localOnly)).isEmpty();
        assertThat(snapshot.get(KsetContextKeys.LANGUAGE)).contains("zh-CN");
    }

    @Test
    void currentValuesAreReadOnlyCopy() {
        KsetContext.put(KsetContextKeys.LANGUAGE, "zh-CN");
        Map<String, Object> values = KsetContext.currentValues();

        assertThat(values).containsEntry(KsetContextKeys.LANGUAGE.getName(), "zh-CN");
        assertThatThrownBy(() -> values.put("x", "y")).isInstanceOf(UnsupportedOperationException.class);
    }
}
