package com.kset.boot.web.filter;

import com.kset.core.logging.LogMaskingUtil;
import com.kset.core.trace.TraceHeaders;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 可选 HTTP 请求/响应日志（敏感字段脱敏）。
 */
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
        long start = System.currentTimeMillis();
        try {
            filterChain.doFilter(wrappedRequest, wrappedResponse);
        } finally {
            long cost = System.currentTimeMillis() - start;
            if (log.isDebugEnabled()) {
                String body = new String(wrappedRequest.getContentAsByteArray(), StandardCharsets.UTF_8);
                log.debug("HTTP {} {} status={} costMs={} traceId={} body={}",
                        request.getMethod(),
                        request.getRequestURI(),
                        wrappedResponse.getStatus(),
                        cost,
                        MDC.get(TraceHeaders.TRACE_ID_KEY),
                        LogMaskingUtil.maskText(body));
            }
            wrappedResponse.copyBodyToResponse();
        }
    }
}
