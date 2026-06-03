package com.kset.web;

import com.kset.web.util.IpUtil;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class IpUtilTest {

    @Test
    void returnsFirstForwardedIp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", " 10.0.0.1, 10.0.0.2 ");

        assertThat(IpUtil.getClientIp(request)).isEqualTo("10.0.0.1");
    }

    @Test
    void fallsBackWhenForwardedIpIsUnknown() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "unknown");
        request.addHeader("Proxy-Client-IP", "192.168.1.10");

        assertThat(IpUtil.getClientIp(request)).isEqualTo("192.168.1.10");
    }
}
