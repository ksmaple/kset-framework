package com.kset.web;

import com.kset.common.monitor.Monitor;
import com.kset.common.trace.TraceHeaders;
import com.kset.web.config.KsetWebProperties;
import com.kset.common.monitor.backend.LogBackend;
import com.kset.common.monitor.internal.DefaultMonitorFacade;
import com.kset.common.monitor.reporter.NoOpMetricAggregator;
import com.kset.common.monitor.sampler.RateSampler;
import com.kset.web.advice.TraceIdResponseBodyAdvice;
import com.kset.web.response.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class TraceIdResponseBodyAdviceTest {

    private final TraceIdResponseBodyAdvice advice = new TraceIdResponseBodyAdvice(new KsetWebProperties());

    @BeforeEach
    void installMonitorFacade() {
        Monitor.install(new DefaultMonitorFacade(new LogBackend(), new RateSampler(1.0), new NoOpMetricAggregator()));
    }

    @AfterEach
    void clearTrace() {
        Monitor.clear();
    }

    @Test
    void supportsResponseEntityApiResponse() throws Exception {
        Method method = SampleController.class.getDeclaredMethod("responseEntity");
        MethodParameter returnType = new MethodParameter(method, -1);

        assertThat(advice.supports(returnType, null)).isTrue();
    }

    @Test
    void fillsTraceIdForApiResponseBody() throws Exception {
        Monitor.setTraceId("trace-web");
        Method method = SampleController.class.getDeclaredMethod("apiResponse");
        MethodParameter returnType = new MethodParameter(method, -1);
        ApiResponse<String> body = ApiResponse.success("ok");

        Object result = advice.beforeBodyWrite(body, returnType, null, null, null, null);

        assertThat(result).isSameAs(body);
        assertThat(body.getTraceId()).isEqualTo("trace-web");
    }

    @Test
    void marksFailedApiResponseEvenWhenTraceIdBodyFieldIsDisabled() throws Exception {
        KsetWebProperties properties = new KsetWebProperties();
        properties.getResponse().setTraceIdEnabled(false);
        TraceIdResponseBodyAdvice disabledAdvice = new TraceIdResponseBodyAdvice(properties);
        Method method = SampleController.class.getDeclaredMethod("apiResponse");
        MockHttpServletRequest request = new MockHttpServletRequest();
        ApiResponse<String> body = ApiResponse.fail(503, "服务暂时不可用");

        disabledAdvice.beforeBodyWrite(body, new MethodParameter(method, -1), null, null,
                new ServletServerHttpRequest(request), null);

        assertThat(request.getAttribute(TraceHeaders.HTTP_FAILURE_ATTRIBUTE)).isEqualTo(true);
        assertThat(body.getTraceId()).isNull();
    }

    static class SampleController {

        ApiResponse<String> apiResponse() {
            return ApiResponse.success("ok");
        }

        ResponseEntity<ApiResponse<String>> responseEntity() {
            return ResponseEntity.ok(ApiResponse.success("ok"));
        }
    }
}
