package com.kset.common.context;

import com.kset.common.auth.LoginContext;
import com.kset.common.auth.LoginUser;
import com.kset.common.monitor.Monitor;
import com.kset.common.trace.TraceHeaders;
import com.kset.common.utils.thread.KsetThreadPoolExecutor;
import com.kset.common.utils.thread.MdcThreadPoolTraceAdapter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class KsetThreadPoolContextTest {

    @AfterEach
    void clear() {
        KsetContext.clear();
        Monitor.clear();
    }

    @Test
    void propagatesLoginAndTraceContextToWorker() throws Exception {
        KsetThreadPoolExecutor executor = KsetThreadPoolExecutor.newBuilder("context-test")
                .corePoolSize(1)
                .maximumPoolSize(1)
                .queueCapacity(10)
                .traceContextAdapter(new MdcThreadPoolTraceAdapter())
                .build();
        try {
            LoginContext.bind(LoginUser.builder().userId("u1").build());
            Monitor.setTraceId("trace-1");
            Monitor.setSpanId("span-1");
            Monitor.setGrayTag("gray");

            Future<String> future = executor.submit(() -> LoginContext.currentUserId().orElse("-")
                    + "|" + Monitor.currentTraceId().orElse("-")
                    + "|" + MDC.get(TraceHeaders.TRACE_ID_KEY)
                    + "|" + KsetContext.get(KsetContextKeys.SPAN_ID).orElse("-")
                    + "|" + KsetContext.get(KsetContextKeys.GRAY_TAG).orElse("-"));

            assertThat(future.get(5, TimeUnit.SECONDS)).isEqualTo("u1|trace-1|trace-1|span-1|gray");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void clearsContextBetweenReusedWorkerTasks() throws Exception {
        KsetThreadPoolExecutor executor = KsetThreadPoolExecutor.newBuilder("context-cleanup-test")
                .corePoolSize(1)
                .maximumPoolSize(1)
                .queueCapacity(10)
                .traceContextAdapter(new MdcThreadPoolTraceAdapter())
                .build();
        try {
            LoginContext.bind(LoginUser.builder().userId("u1").build());
            Monitor.setTraceId("trace-1");
            Future<String> first = executor.submit(() -> LoginContext.currentUserId().orElse("-")
                    + "|" + Monitor.currentTraceId().orElse("-")
                    + "|" + String.valueOf(MDC.get(TraceHeaders.TRACE_ID_KEY)));
            assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo("u1|trace-1|trace-1");

            LoginContext.clear();
            Monitor.clear();
            Future<String> second = executor.submit(() -> LoginContext.currentUserId().orElse("-")
                    + "|" + Monitor.currentTraceId().orElse("-")
                    + "|" + String.valueOf(MDC.get(TraceHeaders.TRACE_ID_KEY)));
            assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo("-|-|null");
        } finally {
            executor.shutdownNow();
        }
    }
}
