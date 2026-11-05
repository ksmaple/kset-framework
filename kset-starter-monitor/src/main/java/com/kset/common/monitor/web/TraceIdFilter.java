package com.kset.common.monitor.web;

import com.kset.common.monitor.HttpTraceBinding;
import com.kset.common.monitor.Monitor;
import com.kset.common.trace.TraceHeaders;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;


public class TraceIdFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(TraceIdFilter.class);

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String incomingTraceId = httpRequest.getHeader(TraceHeaders.TRACE_ID_HEADER);
        HttpTraceBinding binding = Monitor.bindHttpIncoming(incomingTraceId);
        if (binding.getTraceId() != null && !binding.getTraceId().isBlank()) {
            httpResponse.setHeader(binding.getTraceIdHeaderName(), binding.getTraceId());
        }
        if (binding.getSpanId() != null && !binding.getSpanId().isBlank()) {
            httpResponse.setHeader(binding.getSpanIdHeaderName(), binding.getSpanId());
        }

        try {
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException | Error error) {
            try {
                Integer httpStatus = httpResponse.isCommitted() ? httpResponse.getStatus() : null;
                log.error("http exception outcome=FAIL method={} path={} httpStatus={} traceId={} errorType={}",
                        httpRequest.getMethod(), httpRequest.getRequestURI(), httpStatus,
                        binding.getTraceId(), error.getClass().getName(), error);
            } catch (RuntimeException ignored) {
                // Preserve the downstream exception.
            }
            throw error;
        } finally {
            Monitor.clear();
        }
    }
}
