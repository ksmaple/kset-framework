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

import java.io.IOException;


public class TraceIdFilter implements Filter {

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
        } finally {
            Monitor.clear();
        }
    }
}
