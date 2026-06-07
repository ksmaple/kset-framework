package com.kset.auth;

import com.kset.auth.config.KsetAuthProperties;
import com.kset.auth.core.AuthRuleResolver;
import com.kset.auth.core.DefaultLoginUserHeaderCodec;
import com.kset.auth.core.DefaultLoginUserHeaderCodec.BasicLoginContext;
import com.kset.auth.core.LoginAuthService;
import com.kset.auth.core.NoneAuthenticator;
import com.kset.auth.core.AppTokenAuthenticator;
import com.kset.auth.core.SessionAuthenticator;
import com.kset.auth.core.SignatureAuthenticator;
import com.kset.auth.core.TrustedHeaderAuthenticator;
import com.kset.auth.annotation.SkipAuth;
import com.kset.common.utils.sign.KsetSignUtil;
import com.kset.auth.session.LoginSessionStore;
import com.kset.auth.web.DefaultServletAuthFailureHandler;
import com.kset.auth.web.LoginAuthFilter;
import com.alibaba.fastjson2.JSON;
import com.kset.common.auth.AuthHeaders;
import com.kset.common.auth.LoginContext;
import com.kset.common.auth.LoginUser;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.HandlerMapping;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoginAuthFilterTest {

    @AfterEach
    void clear() {
        LoginContext.clear();
    }

    @Test
    void rejectsMissingToken() throws Exception {
        LoginSessionStore store = mock(LoginSessionStore.class);
        LoginAuthFilter filter = filter(new KsetAuthProperties(), store);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest("GET", "/api/orders"), response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(response.getContentAsString()).contains("\"code\":401");
    }

    @Test
    void bindsUserFromRedisSession() throws Exception {
        LoginSessionStore store = mock(LoginSessionStore.class);
        when(store.findByToken("t1")).thenReturn(Optional.of(LoginUser.builder().userId("u1").build()));
        LoginAuthFilter filter = filter(new KsetAuthProperties(), store);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
        request.addHeader(AuthHeaders.SESSION_TOKEN, "t1");
        CapturingFilterChain chain = new CapturingFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.userId).isEqualTo("u1");
        assertThat(chain.subjectType).isEqualTo("app");
        assertThat(LoginContext.currentUser()).isEmpty();
    }

    @Test
    void cmsRuleUsesAdminSubjectAndTokenHeader() throws Exception {
        LoginSessionStore store = mock(LoginSessionStore.class);
        when(store.findByToken("admin-token")).thenReturn(Optional.of(LoginUser.builder().userId("admin-1").build()));
        KsetAuthProperties properties = new KsetAuthProperties();
        KsetAuthProperties.AuthRule rule = new KsetAuthProperties.AuthRule();
        rule.setName("cms");
        rule.setPaths(List.of("/api/cms/**"));
        rule.setSubject("admin");
        rule.setScheme("session");
        rule.setTokenHeader("X-Admin-Session-Token");
        properties.setRules(List.of(rule));
        LoginAuthFilter filter = filter(properties, store);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/cms/user/list");
        request.addHeader("X-Admin-Session-Token", "admin-token");
        CapturingFilterChain chain = new CapturingFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.userId).isEqualTo("admin-1");
        assertThat(chain.subjectType).isEqualTo("admin");
    }

    @Test
    void trustedHeaderModeBindsUserFromHeaders() throws Exception {
        KsetAuthProperties properties = new KsetAuthProperties();
        properties.getWeb().setMode(KsetAuthProperties.Mode.TRUSTED_HEADER);
        LoginAuthFilter filter = filter(properties, mock(LoginSessionStore.class));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
        BasicLoginContext context = BasicLoginContext.from(LoginUser.builder()
                .userId("u1")
                .build(), false);
        request.addHeader(AuthHeaders.LOGIN_CONTEXT, JSON.toJSONString(context));
        request.addHeader(AuthHeaders.DEVICE_ID, "device-1");
        request.addHeader(AuthHeaders.LANGUAGE, "zh-CN");
        CapturingFilterChain chain = new CapturingFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.userId).isEqualTo("u1");
        assertThat(chain.subjectType).isEqualTo("app");
        assertThat(chain.deviceId).isEqualTo("device-1");
        assertThat(chain.language).isEqualTo("zh-CN");
    }

    @Test
    void trustedHeaderModeStillAcceptsLegacySplitHeaders() throws Exception {
        KsetAuthProperties properties = new KsetAuthProperties();
        properties.getWeb().setMode(KsetAuthProperties.Mode.TRUSTED_HEADER);
        LoginAuthFilter filter = filter(properties, mock(LoginSessionStore.class));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
        request.addHeader(AuthHeaders.USER_ID, "u1");
        request.addHeader(AuthHeaders.DEVICE_ID, "device-1");
        request.addHeader(AuthHeaders.LANGUAGE, "zh-CN");
        CapturingFilterChain chain = new CapturingFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.userId).isEqualTo("u1");
        assertThat(chain.deviceId).isEqualTo("device-1");
        assertThat(chain.language).isEqualTo("zh-CN");
    }

    @Test
    void publicPathBypassesAuth() throws Exception {
        LoginAuthFilter filter = filter(new KsetAuthProperties(), mock(LoginSessionStore.class));
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(new MockHttpServletRequest("GET", "/api/public/ping"), new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(LoginContext.currentUser()).isEmpty();
    }

    @Test
    void skipAuthMethodBypassesSessionValidation() throws Exception {
        LoginSessionStore store = mock(LoginSessionStore.class);
        PublicController controller = new PublicController();
        Method method = PublicController.class.getDeclaredMethod("ping");
        HandlerMapping handlerMapping = mock(HandlerMapping.class);
        when(handlerMapping.getHandler(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new HandlerExecutionChain(new HandlerMethod(controller, method)));
        LoginAuthFilter filter = filter(new KsetAuthProperties(), store, List.of(handlerMapping));
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(new MockHttpServletRequest("GET", "/api/orders"), new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
        verify(store, never()).findByToken(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void existingLoginContextBypassesSessionValidation() throws Exception {
        LoginSessionStore store = mock(LoginSessionStore.class);
        LoginAuthFilter filter = filter(new KsetAuthProperties(), store);
        LoginContext.bind(LoginUser.builder().userId("existing").build());
        CapturingFilterChain chain = new CapturingFilterChain();

        filter.doFilter(new MockHttpServletRequest("GET", "/api/orders"), new MockHttpServletResponse(), chain);

        assertThat(chain.userId).isEqualTo("existing");
        assertThat(LoginContext.currentUserId()).contains("existing");
        verify(store, never()).findByToken(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void appTokenRuleAuthenticatesConfiguredApp() throws Exception {
        KsetAuthProperties properties = appKeyProperties("partner-app", "app-secret", "app-token");
        KsetAuthProperties.AuthRule rule = new KsetAuthProperties.AuthRule();
        rule.setName("partner");
        rule.setPaths(List.of("/openapi/**"));
        rule.setSubject("partner");
        rule.setScheme("app-token");
        properties.setRules(List.of(rule));
        LoginAuthFilter filter = filter(properties, mock(LoginSessionStore.class));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/openapi/orders");
        request.addHeader("X-App-Key", "partner-app");
        request.addHeader("X-App-Token", "app-token");
        CapturingFilterChain chain = new CapturingFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.userId).isEqualTo("partner-user");
        assertThat(chain.subjectType).isEqualTo("partner");
    }

    @Test
    void signatureRuleAuthenticatesConfiguredApp() throws Exception {
        KsetAuthProperties properties = appKeyProperties("partner-app", "app-secret", "app-token");
        KsetAuthProperties.AuthRule rule = new KsetAuthProperties.AuthRule();
        rule.setName("partner-sign");
        rule.setPaths(List.of("/openapi/**"));
        rule.setSubject("partner");
        rule.setScheme("signature");
        properties.setRules(List.of(rule));
        LoginAuthFilter filter = filter(properties, mock(LoginSessionStore.class));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/openapi/orders");
        request.addParameter("orderId", "o1");
        request.addHeader("X-App-Key", "partner-app");
        request.addHeader("X-Sign", KsetSignUtil.of("app-secret").signSha1(Map.of(
                "appKey", "partner-app",
                "method", "GET",
                "orderId", "o1")));
        CapturingFilterChain chain = new CapturingFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.userId).isEqualTo("partner-user");
        assertThat(chain.subjectType).isEqualTo("partner");
    }

    private LoginAuthFilter filter(KsetAuthProperties properties, LoginSessionStore store) {
        return filter(properties, store, List.of());
    }

    private LoginAuthFilter filter(KsetAuthProperties properties, LoginSessionStore store, List<HandlerMapping> handlerMappings) {
        return new LoginAuthFilter(properties,
                new LoginAuthService(store,
                        new AuthRuleResolver(properties),
                        List.of(new SessionAuthenticator(store),
                                new TrustedHeaderAuthenticator(new DefaultLoginUserHeaderCodec()),
                                new SignatureAuthenticator(properties),
                                new AppTokenAuthenticator(properties),
                                new NoneAuthenticator())),
                new DefaultServletAuthFailureHandler(),
                handlerMappings);
    }

    private KsetAuthProperties appKeyProperties(String appKey, String secret, String token) {
        KsetAuthProperties properties = new KsetAuthProperties();
        KsetAuthProperties.App app = new KsetAuthProperties.App();
        app.setAppKey(appKey);
        app.setSecret(secret);
        app.setToken(token);
        app.setSubject("partner");
        app.setUserId("partner-user");
        app.setRoles(List.of("partner"));
        properties.getAppKey().setApps(List.of(app));
        return properties;
    }

    private static final class CapturingFilterChain extends MockFilterChain {
        private String userId;
        private String subjectType;
        private String deviceId;
        private String language;

        @Override
        public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response) {
            LoginUser user = LoginContext.currentUser().orElse(null);
            userId = user != null ? user.getUserId() : null;
            subjectType = user != null ? user.getSubjectType() : null;
            deviceId = user != null ? user.getDeviceId() : null;
            language = user != null ? user.getLanguage() : null;
        }
    }

    private static class PublicController {
        @SkipAuth
        void ping() {
        }
    }
}
