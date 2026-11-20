package com.kset.common.net;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpTest {

    @Test
    void prefersFirstForwardedHop() {
        Map<String, String> headers = Map.of("X-Forwarded-For", " 10.0.0.1, 10.0.0.2 ");

        assertThat(ClientIp.resolve(headers::get, "192.168.1.1")).isEqualTo("10.0.0.1");
    }

    @Test
    void usesRealIpBeforeProxyClientHeader() {
        Map<String, String> headers = Map.of(
                "X-Forwarded-For", "unknown",
                "X-Real-IP", "203.0.113.8",
                "Proxy-Client-IP", "192.168.1.10");

        assertThat(ClientIp.resolve(headers::get, null)).isEqualTo("203.0.113.8");
    }

    @Test
    void mapsIpv6Loopback() {
        assertThat(ClientIp.resolve(name -> null, "0:0:0:0:0:0:0:1")).isEqualTo("127.0.0.1");
        assertThat(ClientIp.isInternal("10.1.2.3")).isTrue();
        assertThat(ClientIp.isInternal("8.8.8.8")).isFalse();
    }
}
