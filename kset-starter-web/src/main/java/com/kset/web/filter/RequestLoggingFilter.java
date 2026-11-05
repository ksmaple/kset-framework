package com.kset.web.filter;

import com.kset.common.auth.LoginContext;
import com.kset.common.monitor.Monitor;
import com.kset.common.monitor.TraceSnapshot;
import com.kset.common.monitor.facade.MonitorStatus;
import com.kset.common.monitor.facade.MonitorTypes;
import com.kset.common.trace.TraceHeaders;
import com.kset.web.logging.RequestLogParameters;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;

/**
 * HTTP 请求结果和脱敏参数日志。
 */
@Slf4j
public class RequestLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        ContentCachingRequestWrapper wrappedRequest =
                new ContentCachingRequestWrapper(request, RequestLogParameters.MAX_BYTES);
        TraceSnapshot entryTrace = Monitor.capture();
        long start = System.nanoTime();
        boolean failedWithException = false;
        try {
            filterChain.doFilter(wrappedRequest, response);
        } catch (IOException | ServletException | RuntimeException | Error error) {
            failedWithException = true;
            throw error;
        } finally {
            try {
                logRequest(request, wrappedRequest, response, entryTrace, start, failedWithException);
            } catch (RuntimeException ignored) {
                // Logging must not change the HTTP response or replace a downstream exception.
            }
        }
    }

    private void logRequest(HttpServletRequest request, ContentCachingRequestWrapper wrappedRequest,
                            HttpServletResponse response, TraceSnapshot entryTrace, long start,
                            boolean failedWithException) {
        long cost = (System.nanoTime() - start) / 1_000_000L;
        boolean businessFailure = Boolean.TRUE.equals(request.getAttribute(TraceHeaders.HTTP_FAILURE_ATTRIBUTE));
        boolean failed = failedWithException || businessFailure || response.getStatus() >= 400;
        if (failed || cost >= 500 || log.isInfoEnabled()) {
            String route = RequestLogParameters.route(wrappedRequest);
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
            String grayTag = request.getAttribute(TraceHeaders.GRAY_TAG_KEY) instanceof String value
                    ? value : entryTrace.getGrayTag();
            TraceSnapshot trace = new TraceSnapshot(traceId, spanId, grayTag);
            try (var ignored = Monitor.openScope(trace)) {
                if (cost >= 500) {
                    Monitor.logEvent(MonitorTypes.URL, "request-log", MonitorStatus.FAIL,
                            cost + "ms " + request.getMethod() + " " + route);
                }
                String userId = LoginContext.currentUserId().orElseGet(() ->
                        request.getAttribute(TraceHeaders.LOGIN_USER_ID_ATTRIBUTE) instanceof String value ? value : null);
                String subjectType = LoginContext.currentSubjectType().orElseGet(() ->
                        request.getAttribute(TraceHeaders.LOGIN_SUBJECT_TYPE_ATTRIBUTE) instanceof String value ? value : null);
                String parameters = RequestLogParameters.capture(wrappedRequest);
                Object businessCode = request.getAttribute(TraceHeaders.BUSINESS_CODE_ATTRIBUTE);
                Integer httpStatus = failedWithException && !response.isCommitted() ? null : response.getStatus();
                if (failed || cost >= 500) {
                    log.warn("http request outcome={} method={} path={} httpStatus={} businessCode={} costMs={} traceId={} loginState={} operatorUid={} subjectType={} {}",
                            failed ? "FAIL" : "SLOW", request.getMethod(), route,
                            httpStatus, businessCode, cost, traceId,
                            userId != null ? "AUTHENTICATED" : "ANONYMOUS", userId, subjectType, parameters);
                } else {
                    log.info("http request outcome=SUCCESS method={} path={} httpStatus={} costMs={} traceId={} loginState={} operatorUid={} subjectType={} {}",
                            request.getMethod(), route, httpStatus, cost, traceId,
                            userId != null ? "AUTHENTICATED" : "ANONYMOUS", userId, subjectType, parameters);
                }
            }
        }
    }
}
