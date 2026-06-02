package com.kset.auth;

import com.kset.auth.config.KsetAuthProperties;
import com.kset.auth.core.AuthRuleResolver;
import com.kset.auth.core.DefaultLoginUserHeaderCodec;
import com.kset.auth.core.DefaultLoginUserHeaderCodec.BasicLoginContext;
import com.kset.auth.core.LoginAuthService;
import com.kset.auth.core.NoneAuthenticator;
import com.kset.auth.core.SessionAuthenticator;
import com.kset.auth.core.TrustedHeaderAuthenticator;
import com.kset.auth.gateway.DefaultGatewayAuthFailureHandler;
import com.kset.auth.gateway.LoginAuthGatewayFilter;
import com.kset.auth.session.LoginSessionStore;
import com.kset.common.auth.AuthHeaders;
import com.kset.common.auth.LoginUser;
import com.alibaba.fastjson2.JSON;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LoginAuthGatewayFilterTest {

    @Test
    void writesIdentityHeadersForValidSession() {
        LoginSessionStore store = mock(LoginSessionStore.class);
        when(store.findByToken("t1")).thenReturn(Optional.of(LoginUser.builder()
                .userId("u1")
                .userName("neo")
                .deviceId("device-1")
                .ip("10.0.0.1")
                .language("zh-CN")
                .build()));
        LoginAuthGatewayFilter filter = filter(store);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/orders").header(AuthHeaders.SESSION_TOKEN, "t1"));
        CapturingGatewayChain chain = new CapturingGatewayChain();

        filter.filter(exchange, chain).block();

        String context = chain.exchange.getRequest().getHeaders().getFirst(AuthHeaders.APP_LOGIN_CONTEXT);
        BasicLoginContext user = JSON.parseObject(context, BasicLoginContext.class);
        assertThat(user.getUserId()).isEqualTo("u1");
        assertThat(user.getUserName()).isEqualTo("neo");
        assertThat(chain.exchange.getRequest().getHeaders().getFirst(AuthHeaders.AUTH_SUBJECT)).isEqualTo("app");
        assertThat(context).doesNotContain("device-1", "10.0.0.1", "zh-CN");
        assertThat(chain.exchange.getRequest().getHeaders().getFirst(AuthHeaders.DEVICE_ID)).isEqualTo("device-1");
        assertThat(chain.exchange.getRequest().getHeaders().getFirst(AuthHeaders.IP)).isEqualTo("10.0.0.1");
        assertThat(chain.exchange.getRequest().getHeaders().getFirst(AuthHeaders.LANGUAGE)).isEqualTo("zh-CN");
        assertThat(chain.exchange.getRequest().getHeaders().containsKey(AuthHeaders.USER_ID)).isFalse();
    }

    @Test
    void cmsRuleWritesAdminLoginContextOnly() {
        LoginSessionStore store = mock(LoginSessionStore.class);
        when(store.findByToken("admin-token")).thenReturn(Optional.of(LoginUser.builder()
                .userId("admin-1")
                .userName("root")
                .build()));
        KsetAuthProperties properties = new KsetAuthProperties();
        KsetAuthProperties.AuthRule rule = new KsetAuthProperties.AuthRule();
        rule.setName("cms");
        rule.setPaths(List.of("/api/cms/**"));
        rule.setSubject("admin");
        rule.setScheme("session");
        rule.setTokenHeader("X-Admin-Session-Token");
        properties.setRules(List.of(rule));
        LoginAuthGatewayFilter filter = filter(properties, store);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/cms/user/list").header("X-Admin-Session-Token", "admin-token"));
        CapturingGatewayChain chain = new CapturingGatewayChain();

        filter.filter(exchange, chain).block();

        assertThat(chain.exchange.getRequest().getHeaders().getFirst(AuthHeaders.AUTH_SUBJECT)).isEqualTo("admin");
        assertThat(chain.exchange.getRequest().getHeaders().getFirst(AuthHeaders.ADMIN_LOGIN_CONTEXT)).contains("admin-1");
        assertThat(chain.exchange.getRequest().getHeaders().containsKey(AuthHeaders.APP_LOGIN_CONTEXT)).isFalse();
    }

    @Test
    void publicRuleBypassesAuth() {
        LoginAuthGatewayFilter filter = filter(mock(LoginSessionStore.class));
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/public/ping"));
        CapturingGatewayChain chain = new CapturingGatewayChain();

        filter.filter(exchange, chain).block();

        assertThat(chain.exchange).isNotNull();
    }

    @Test
    void rejectsInvalidSession() {
        LoginSessionStore store = mock(LoginSessionStore.class);
        LoginAuthGatewayFilter filter = filter(store);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/orders"));

        filter.filter(exchange, ignored -> Mono.empty()).block();

        assertThat(exchange.getResponse().getBodyAsString().block()).contains("\"code\":401");
    }

    private LoginAuthGatewayFilter filter(LoginSessionStore store) {
        return filter(new KsetAuthProperties(), store);
    }

    private LoginAuthGatewayFilter filter(KsetAuthProperties properties, LoginSessionStore store) {
        DefaultLoginUserHeaderCodec codec = new DefaultLoginUserHeaderCodec();
        return new LoginAuthGatewayFilter(properties,
                new LoginAuthService(store,
                        new AuthRuleResolver(properties),
                        List.of(new SessionAuthenticator(store), new TrustedHeaderAuthenticator(codec), new NoneAuthenticator())),
                new DefaultLoginUserHeaderCodec(),
                new DefaultGatewayAuthFailureHandler());
    }

    private static final class CapturingGatewayChain implements GatewayFilterChain {
        private ServerWebExchange exchange;

        @Override
        public Mono<Void> filter(ServerWebExchange exchange) {
            this.exchange = exchange;
            return Mono.empty();
        }
    }
}
