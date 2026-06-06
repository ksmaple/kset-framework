package com.kset.auth;

import com.kset.auth.autoconfigure.KsetAuthAopAutoConfiguration;
import com.kset.auth.autoconfigure.KsetAuthAutoConfiguration;
import com.kset.auth.autoconfigure.KsetAuthDubboAutoConfiguration;
import com.kset.auth.autoconfigure.KsetAuthGatewayAutoConfiguration;
import com.kset.auth.autoconfigure.KsetAuthRedisSessionAutoConfiguration;
import com.kset.auth.autoconfigure.KsetAuthWebAutoConfiguration;
import com.kset.auth.core.AuthRuleResolver;
import com.kset.auth.core.AppTokenAuthenticator;
import com.kset.auth.core.LoginAuthService;
import com.kset.auth.core.SignatureAuthenticator;
import com.kset.auth.session.LoginSessionStore;
import com.kset.auth.session.RedisLoginSessionStore;
import com.kset.auth.spi.PermissionChecker;
import com.kset.redis.core.KsetRedisService;
import com.kset.auth.web.LoginAuthFilter;
import com.kset.common.auth.LoginUser;
import org.apache.dubbo.rpc.Filter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class KsetAuthAutoConfigurationTest {

    @Test
    void createsCoreBeansWithCustomSessionStore() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(KsetAuthAutoConfiguration.class))
                .withUserConfiguration(TestSessionStoreConfiguration.class)
                .run(context -> assertThat(context).hasSingleBean(LoginAuthService.class)
                        .hasSingleBean(AuthRuleResolver.class)
                        .hasSingleBean(SignatureAuthenticator.class)
                        .hasSingleBean(AppTokenAuthenticator.class));
    }

    @Test
    void createsServletFilterInWebApplication() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        KsetAuthAutoConfiguration.class,
                        KsetAuthWebAutoConfiguration.class))
                .withUserConfiguration(TestSessionStoreConfiguration.class)
                .run(context -> assertThat(context).hasSingleBean(LoginAuthFilter.class));
    }

    @Test
    void createsGatewayFilterInReactiveApplication() {
        new ReactiveWebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        KsetAuthAutoConfiguration.class,
                        KsetAuthGatewayAutoConfiguration.class))
                .withUserConfiguration(TestSessionStoreConfiguration.class)
                .run(context -> assertThat(context).hasBean("loginAuthGatewayFilter")
                        .getBean("loginAuthGatewayFilter")
                        .isInstanceOf(GlobalFilter.class));
    }

    @Test
    void createsDubboFilterWhenDubboPresent() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        KsetAuthAutoConfiguration.class,
                        KsetAuthDubboAutoConfiguration.class))
                .withUserConfiguration(TestSessionStoreConfiguration.class)
                .run(context -> assertThat(context).hasBean("loginContextDubboFilter")
                        .getBean("loginContextDubboFilter")
                        .isInstanceOf(Filter.class));
    }

    @Test
    void createsAopAspectWhenAspectPresent() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        KsetAuthAutoConfiguration.class,
                        KsetAuthAopAutoConfiguration.class))
                .withUserConfiguration(TestSessionStoreConfiguration.class)
                .run(context -> assertThat(context).hasBean("loginAuthAspect")
                        .hasSingleBean(PermissionChecker.class));
    }

    @Test
    void startsWithoutSessionStoreWhenNoStoreComponentPresent() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        KsetAuthAutoConfiguration.class,
                        KsetAuthRedisSessionAutoConfiguration.class))
                .run(context -> assertThat(context)
                        .hasSingleBean(LoginAuthService.class)
                        .doesNotHaveBean(LoginSessionStore.class));
    }

    @Test
    void createsRedisSessionStoreWhenRedisBeanExists() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        KsetAuthAutoConfiguration.class,
                        KsetAuthRedisSessionAutoConfiguration.class))
                .withBean(KsetRedisService.class, () -> mock(KsetRedisService.class))
                .run(context -> assertThat(context)
                        .hasSingleBean(LoginSessionStore.class)
                        .getBean(LoginSessionStore.class)
                        .isInstanceOf(RedisLoginSessionStore.class));
    }

    @Test
    void createsRedisSessionStoreWhenExplicitlyConfigured() {
        new ApplicationContextRunner()
                .withPropertyValues("kset.auth.session.store-type=redis")
                .withConfiguration(AutoConfigurations.of(
                        KsetAuthAutoConfiguration.class,
                        KsetAuthRedisSessionAutoConfiguration.class))
                .withBean(KsetRedisService.class, () -> mock(KsetRedisService.class))
                .run(context -> assertThat(context)
                        .hasSingleBean(LoginSessionStore.class)
                        .getBean(LoginSessionStore.class)
                        .isInstanceOf(RedisLoginSessionStore.class));
    }

    @Configuration
    static class TestSessionStoreConfiguration {
        @Bean
        LoginSessionStore loginSessionStore() {
            return new LoginSessionStore() {
                @Override
                public Optional<LoginUser> findByToken(String token) {
                    return Optional.empty();
                }

                @Override
                public void save(String token, LoginUser user, Duration ttl) {
                }

                @Override
                public void delete(String token) {
                }

                @Override
                public void refresh(String token, Duration ttl) {
                }
            };
        }
    }
}
