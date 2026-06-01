package com.kset.web.advice;

import com.kset.common.monitor.Monitor;
import com.kset.web.response.ApiResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 为 {@link ApiResponse} 自动填充 traceId（经 {@link Monitor} 门面）。
 */
@RestControllerAdvice
@ConditionalOnClass(ApiResponse.class)
@ConditionalOnProperty(prefix = "kset.web.response", name = "trace-id-enabled", havingValue = "true", matchIfMissing = true)
public class TraceIdResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        Class<?> parameterType = returnType.getParameterType();
        return ApiResponse.class.isAssignableFrom(parameterType) || ResponseEntity.class.isAssignableFrom(parameterType);
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (body instanceof ApiResponse<?> apiResponse) {
            if (apiResponse.getTraceId() == null || apiResponse.getTraceId().isBlank()) {
                apiResponse.setTraceId(Monitor.currentTraceId().orElse(null));
            }
            return apiResponse;
        }
        return body;
    }
}
