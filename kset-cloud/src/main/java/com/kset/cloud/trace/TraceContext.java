package com.kset.cloud.trace;

import com.kset.core.trace.TraceHeaders;
import org.slf4j.MDC;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

import java.util.Optional;
import java.util.UUID;

/**
 * 跨组件链路上下文（Servlet MDC + Reactor Context）
 */
public final class TraceContext {

    public static final String TRACE_ID_KEY = TraceHeaders.TRACE_ID_KEY;
    public static final String SPAN_ID_KEY = TraceHeaders.SPAN_ID_KEY;
    public static final String GRAY_TAG_KEY = TraceHeaders.GRAY_TAG_KEY;
    public static final String TRACE_ID_HEADER = TraceHeaders.TRACE_ID_HEADER;
    public static final String SPAN_ID_HEADER = TraceHeaders.SPAN_ID_HEADER;
    public static final String GRAY_TAG_HEADER = TraceHeaders.GRAY_TAG_HEADER;

    private TraceContext() {
    }

    public static String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static String generateSpanId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    public static void setTraceId(String traceId) {
        if (traceId != null) {
            MDC.put(TRACE_ID_KEY, traceId);
        }
    }

    public static void setSpanId(String spanId) {
        if (spanId != null) {
            MDC.put(SPAN_ID_KEY, spanId);
        }
    }

    public static void setGrayTag(String grayTag) {
        if (grayTag != null) {
            MDC.put(GRAY_TAG_KEY, grayTag);
        }
    }

    public static Optional<String> getTraceId() {
        return Optional.ofNullable(MDC.get(TRACE_ID_KEY));
    }

    public static Optional<String> getGrayTag() {
        return Optional.ofNullable(MDC.get(GRAY_TAG_KEY));
    }

    public static void clear() {
        MDC.remove(TRACE_ID_KEY);
        MDC.remove(SPAN_ID_KEY);
        MDC.remove(GRAY_TAG_KEY);
    }

    public static Context putReactorContext(Context context, String traceId, String grayTag) {
        Context updated = context;
        if (traceId != null) {
            updated = updated.put(TRACE_ID_KEY, traceId);
        }
        if (grayTag != null) {
            updated = updated.put(GRAY_TAG_KEY, grayTag);
        }
        return updated;
    }

    public static Optional<String> getFromReactor(ContextView contextView, String key) {
        return contextView.hasKey(key) ? Optional.of(contextView.get(key)) : Optional.empty();
    }
}
