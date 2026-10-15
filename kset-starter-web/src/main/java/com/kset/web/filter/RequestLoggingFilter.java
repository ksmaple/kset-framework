package com.kset.web.filter;

import com.kset.common.logging.LogMaskingUtil;
import com.kset.common.monitor.Monitor;
import com.kset.common.monitor.TraceSnapshot;
import com.kset.common.monitor.facade.MonitorStatus;
import com.kset.common.monitor.facade.MonitorTypes;
import com.kset.common.trace.TraceHeaders;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 可选 HTTP 请求/响应日志（敏感字段脱敏）。
 */
@Slf4j
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final int REQUEST_BODY_CACHE_LIMIT = 64 * 1024;
    private static final String CONTENT_TYPE_JSON = "json";
    private static final String CONTENT_TYPE_TEXT = "text";
    private static final String CONTENT_TYPE_FORM = "x-www-form-urlencoded";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request, REQUEST_BODY_CACHE_LIMIT);
        TraceSnapshot entryTrace = Monitor.capture();
        long start = System.nanoTime();
        try {
            filterChain.doFilter(wrappedRequest, response);
        } finally {
            long cost = (System.nanoTime() - start) / 1_000_000L;
            if (cost >= 500 || log.isDebugEnabled()) {
                String traceId = response.getHeader(TraceHeaders.TRACE_ID_HEADER);
                if (traceId == null || traceId.isBlank()) {
                    traceId = entryTrace.getTraceId();
                }
                if (traceId == null || traceId.isBlank()) {
                    traceId = Monitor.currentTraceId().orElse(null);
                }
                String spanId = response.getHeader(TraceHeaders.SPAN_ID_HEADER);
                if (spanId == null || spanId.isBlank()) {
                    spanId = entryTrace.getSpanId();
                }
                TraceSnapshot trace = new TraceSnapshot(traceId,
                        spanId, entryTrace.getGrayTag());
                try (var ignored = Monitor.openScope(trace)) {
                    if (cost >= 500) {
                        Monitor.logEvent(MonitorTypes.URL, "request-log", MonitorStatus.FAIL,
                                cost + "ms " + request.getMethod() + " " + request.getRequestURI());
                    }
                    if (log.isDebugEnabled()) {
                        String body = readBody(wrappedRequest);
                        log.debug("HTTP {} {} status={} costMs={} traceId={} body={}",
                                request.getMethod(),
                                request.getRequestURI(),
                                response.getStatus(),
                                cost,
                                Monitor.currentTraceId().orElse(null),
                                LogMaskingUtil.maskText(body));
                    }
                }
            }
        }
    }

    private String readBody(ContentCachingRequestWrapper request) {
        if (!isVisibleContent(request.getContentType())) {
            return "[content omitted]";
        }
        byte[] content = request.getContentAsByteArray();
        if (content.length == 0) {
            return "";
        }
        return new String(content, StandardCharsets.UTF_8);
    }

    private boolean isVisibleContent(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return true;
        }
        String value = contentType.toLowerCase();
        return value.contains(CONTENT_TYPE_JSON)
                || value.contains(CONTENT_TYPE_TEXT)
                || value.contains(CONTENT_TYPE_FORM);
    }
}
