package com.kset.gateway.filter;

import com.kset.cloud.config.KsetCloudProperties;
import com.kset.gateway.auth.HeaderTokenGatewayAuthProvider;
import com.kset.gateway.auth.PassThroughGatewayAuthProvider;
import com.kset.gateway.spi.GatewayAuthProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class AuthGatewayFilterTest {

    @Test
    void emptyResultContinuesFilterChain() {
        AuthGatewayFilter filter = new AuthGatewayFilter(providerFactory(new PassThroughGatewayAuthProvider()));
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/orders").build());
        AtomicBoolean continued = new AtomicBoolean(false);
        GatewayFilterChain chain = ignored -> {
            continued.set(true);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertThat(continued).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void missingTokenRejectsWithoutContinuingChain() {
        KsetCloudProperties properties = new KsetCloudProperties();
        properties.getGateway().setAuthTokenHeader("X-Auth-Token");
        AuthGatewayFilter filter = new AuthGatewayFilter(providerFactory(new HeaderTokenGatewayAuthProvider(properties)));
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/orders").build());
        AtomicBoolean continued = new AtomicBoolean(false);

        filter.filter(exchange, ignored -> {
            continued.set(true);
            return Mono.empty();
        }).block();

        assertThat(continued).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void presentTokenContinuesFilterChain() {
        KsetCloudProperties properties = new KsetCloudProperties();
        properties.getGateway().setAuthTokenHeader("X-Auth-Token");
        properties.getGateway().setAuthToken("t1");
        AuthGatewayFilter filter = new AuthGatewayFilter(providerFactory(new HeaderTokenGatewayAuthProvider(properties)));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/orders").header("X-Auth-Token", "t1").build());
        AtomicBoolean continued = new AtomicBoolean(false);

        filter.filter(exchange, ignored -> {
            continued.set(true);
            return Mono.empty();
        }).block();

        assertThat(continued).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void unconfiguredExpectedTokenRejectsEvenIfHeaderPresent() {
        KsetCloudProperties properties = new KsetCloudProperties();
        properties.getGateway().setAuthTokenHeader("X-Auth-Token");
        AuthGatewayFilter filter = new AuthGatewayFilter(providerFactory(new HeaderTokenGatewayAuthProvider(properties)));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/orders").header("X-Auth-Token", "t1").build());
        AtomicBoolean continued = new AtomicBoolean(false);

        filter.filter(exchange, ignored -> {
            continued.set(true);
            return Mono.empty();
        }).block();

        assertThat(continued).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void mismatchedTokenRejectsWithoutContinuingChain() {
        KsetCloudProperties properties = new KsetCloudProperties();
        properties.getGateway().setAuthTokenHeader("X-Auth-Token");
        properties.getGateway().setAuthToken("expected");
        AuthGatewayFilter filter = new AuthGatewayFilter(providerFactory(new HeaderTokenGatewayAuthProvider(properties)));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/orders").header("X-Auth-Token", "other").build());
        AtomicBoolean continued = new AtomicBoolean(false);

        filter.filter(exchange, ignored -> {
            continued.set(true);
            return Mono.empty();
        }).block();

        assertThat(continued).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private static ObjectProvider<GatewayAuthProvider> providerFactory(GatewayAuthProvider provider) {
        return new ObjectProvider<>() {
            @Override
            public GatewayAuthProvider getObject() {
                return provider;
            }

            @Override
            public GatewayAuthProvider getObject(Object... args) {
                return provider;
            }

            @Override
            public Iterator<GatewayAuthProvider> iterator() {
                return List.of(provider).iterator();
            }
        };
    }
}
