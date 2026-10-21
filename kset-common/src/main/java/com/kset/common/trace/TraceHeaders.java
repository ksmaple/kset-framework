package com.kset.common.trace;


public final class TraceHeaders {

    public static final String TRACE_ID_KEY = "traceId";
    public static final String TRACE_HEADER_KEY = "kset.trace.header";
    public static final String HTTP_FAILURE_ATTRIBUTE = "kset.http.failure";
    public static final String SPAN_ID_KEY = "spanId";
    public static final String GRAY_TAG_KEY = "grayTag";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String SPAN_ID_HEADER = "X-Span-Id";
    public static final String GRAY_TAG_HEADER = "X-Gray-Tag";

    private TraceHeaders() {
    }
}
