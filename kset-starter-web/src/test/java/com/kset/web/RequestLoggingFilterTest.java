package com.kset.web;

import com.kset.web.filter.RequestLoggingFilter;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestLoggingFilterTest {

    @Test
    void writesResponseDirectlyToServletResponse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/stream");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new RequestLoggingFilter().doFilter(request, response, (wrappedRequest, wrappedResponse) -> {
            wrappedResponse.getOutputStream().write("first".getBytes(StandardCharsets.UTF_8));
            wrappedResponse.flushBuffer();
            assertThat(response.getContentAsString()).isEqualTo("first");
        });

        assertThat(response.getContentAsString()).isEqualTo("first");
    }

    @Test
    void preservesDownstreamException() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/error");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> new RequestLoggingFilter().doFilter(request, response,
                (wrappedRequest, wrappedResponse) -> { throw new ServletException("downstream"); }))
                .isInstanceOf(ServletException.class)
                .hasMessage("downstream");
    }
}
