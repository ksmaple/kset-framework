package com.kset.auth.web;

import com.kset.common.monitor.Monitor;
import com.kset.common.trace.TraceHeaders;
import com.kset.common.utils.JsonUtil;
import com.kset.web.config.KsetWebProperties;
import com.kset.web.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class DefaultServletAuthFailureHandler implements ServletAuthFailureHandler {

    private final boolean useHttpStatus;

    public DefaultServletAuthFailureHandler() {
        this(new KsetWebProperties());
    }

    public DefaultServletAuthFailureHandler(KsetWebProperties properties) {
        this.useHttpStatus = properties.getExceptionHandling().isUseHttpStatus();
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, int code, String message) {
        response.setStatus(useHttpStatus ? (code == 403 ? HttpStatus.FORBIDDEN.value()
                : HttpStatus.UNAUTHORIZED.value()) : HttpStatus.OK.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ApiResponse<Void> body = ApiResponse.fail(code, message);
        String traceId = Monitor.currentTraceId().orElse(null);
        if (traceId != null && !traceId.isBlank()) {
            body.setTraceId(traceId);
            response.setHeader(TraceHeaders.TRACE_ID_HEADER, traceId);
        }
        try {
            response.getWriter().write(JsonUtil.toJson(body));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write auth failure response", e);
        }
    }
}
