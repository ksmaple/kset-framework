package com.kset.auth;

import com.kset.auth.annotation.SkipAuth;
import com.kset.auth.annotation.RequirePermission;
import com.kset.auth.annotation.RequireRole;
import com.kset.auth.aop.LoginAuthAspect;
import com.kset.auth.core.DefaultPermissionChecker;
import com.kset.common.auth.LoginContext;
import com.kset.common.auth.LoginRequiredException;
import com.kset.common.auth.LoginUser;
import com.kset.common.auth.PermissionDeniedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginAuthAspectTest {

    @AfterEach
    void clear() {
        LoginContext.clear();
    }

    @Test
    void requiresRole() {
        TestService service = proxy();
        LoginContext.bind(LoginUser.builder().userId("u1").subjectType("app").roles(List.of("admin")).build());

        assertThat(service.admin()).isEqualTo("ok");
    }

    @Test
    void subjectCanLimitPermissionCheck() {
        TestService service = proxy();
        LoginContext.bind(LoginUser.builder()
                .userId("admin-1")
                .subjectType("admin")
                .permissions(List.of("cms:user:update"))
                .build());

        assertThat(service.updateCmsUser()).isEqualTo("ok");
    }

    @Test
    void rejectsDifferentSubject() {
        TestService service = proxy();
        LoginContext.bind(LoginUser.builder()
                .userId("app-1")
                .subjectType("app")
                .permissions(List.of("cms:user:update"))
                .build());

        assertThatThrownBy(service::updateCmsUser).isInstanceOf(LoginRequiredException.class);
    }

    @Test
    void rejectsMissingPermission() {
        TestService service = proxy();
        LoginContext.bind(LoginUser.builder().userId("u1").build());

        assertThatThrownBy(service::createOrder).isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void rejectsAnonymousUser() {
        TestService service = proxy();

        assertThatThrownBy(service::admin).isInstanceOf(LoginRequiredException.class);
    }

    @Test
    void skipAuthBypassesMethodPermissionCheck() {
        TestService service = proxy();

        assertThat(service.publicCreateOrder()).isEqualTo("ok");
    }

    @Test
    void skipAuthBypassesClassPermissionCheck() {
        PublicTestService service = publicProxy();

        assertThat(service.admin()).isEqualTo("ok");
    }

    private TestService proxy() {
        AspectJProxyFactory factory = new AspectJProxyFactory(new TestService());
        factory.addAspect(new LoginAuthAspect(new DefaultPermissionChecker()));
        return factory.getProxy();
    }

    private PublicTestService publicProxy() {
        AspectJProxyFactory factory = new AspectJProxyFactory(new PublicTestService());
        factory.addAspect(new LoginAuthAspect(new DefaultPermissionChecker()));
        return factory.getProxy();
    }

    static class TestService {
        @RequireRole("admin")
        String admin() {
            return "ok";
        }

        @RequirePermission("order:create")
        String createOrder() {
            return "ok";
        }

        @RequirePermission(value = "cms:user:update", subject = "admin")
        String updateCmsUser() {
            return "ok";
        }

        @SkipAuth
        @RequirePermission("order:create")
        String publicCreateOrder() {
            return "ok";
        }
    }

    @SkipAuth
    static class PublicTestService {
        @RequireRole("admin")
        String admin() {
            return "ok";
        }
    }
}
