package com.kset.core.trace;

/**
 * 链路追踪与灰度相关 HTTP 头 / MDC 键名（kset-common 与 kset-cloud 共享）。
 */
public final class TraceHeaders {

    public static final String TRACE_ID_KEY = "traceId";
    public static final String SPAN_ID_KEY = "spanId";
    public static final String GRAY_TAG_KEY = "grayTag";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String SPAN_ID_HEADER = "X-Span-Id";
    public static final String GRAY_TAG_HEADER = "X-Gray-Tag";

    private TraceHeaders() {
    }
}
