package com.kset.common.monitor.web;

import com.kset.common.monitor.Monitor;
import com.kset.common.trace.TraceHeaders;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;

/**
 * Servlet 灰度标签注入 MDC，供 LoadBalancer / Dubbo 下游使用。
 */
public class GrayTagServletFilter implements Filter {

    private final String defaultGrayTag;

    public GrayTagServletFilter(String defaultGrayTag) {
        this.defaultGrayTag = defaultGrayTag;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String grayTag = httpRequest.getHeader(TraceHeaders.GRAY_TAG_HEADER);
        Monitor.bindHttpGrayTag(grayTag, defaultGrayTag);
        try {
            chain.doFilter(request, response);
        } finally {
            Monitor.clearHttpGrayTag();
        }
    }
}
