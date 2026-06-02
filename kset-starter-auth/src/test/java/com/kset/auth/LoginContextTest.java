package com.kset.auth;

import com.kset.common.auth.LoginContext;
import com.kset.common.auth.LoginContextScope;
import com.kset.common.auth.LoginContextSnapshot;
import com.kset.common.auth.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginContextTest {

    @AfterEach
    void clear() {
        LoginContext.clear();
    }

    @Test
    void bindsCapturesAndRestoresUser() {
        LoginUser first = LoginUser.builder().userId("u1").roles(List.of("admin")).build();
        LoginUser second = LoginUser.builder().userId("u2").permissions(List.of("order:create")).build();

        LoginContext.bind(first);
        LoginContextSnapshot snapshot = LoginContext.capture();
        LoginContext.bind(second);

        assertThat(LoginContext.currentUserId()).contains("u2");
        LoginContext.restore(snapshot);
        assertThat(LoginContext.currentUserId()).contains("u1");
        assertThat(LoginContext.hasRole("admin")).isTrue();
    }

    @Test
    void scopeRestoresPreviousUser() {
        LoginContext.bind(LoginUser.builder().userId("u1").build());
        LoginContextSnapshot snapshot = new LoginContextSnapshot(LoginUser.builder().userId("u2").build());

        try (LoginContextScope ignored = LoginContext.openScope(snapshot)) {
            assertThat(LoginContext.currentUserId()).contains("u2");
        }

        assertThat(LoginContext.currentUserId()).contains("u1");
    }

    @Test
    void loginUserDefensivelyCopiesCollections() {
        List<String> roles = new ArrayList<>(List.of("admin"));
        Map<String, String> extra = new HashMap<>(Map.of("deptId", "d1"));
        LoginUser user = LoginUser.builder().userId("u1").roles(roles).extra(extra).build();

        roles.add("user");
        extra.put("orgId", "o1");

        assertThat(user.getRoles()).containsExactly("admin");
        assertThat(user.getExtra()).containsOnly(Map.entry("deptId", "d1"));
        assertThatThrownBy(() -> user.getRoles().add("x")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void keepsCommonLoginMetadata() {
        LoginUser user = LoginUser.builder()
                .userId("u1")
                .subjectType("APP")
                .realName("Neo")
                .mobile("13800000000")
                .email("neo@example.com")
                .orgId("org-1")
                .deptId("dept-1")
                .deviceId("device-1")
                .deviceType("mobile")
                .clientType("app")
                .clientVersion("1.0.0")
                .ip("10.0.0.1")
                .language("zh-CN")
                .timeZone("Asia/Shanghai")
                .loginTime(1710000000000L)
                .build();

        assertThat(user.getDeviceId()).isEqualTo("device-1");
        assertThat(user.getLanguage()).isEqualTo("zh-CN");
        assertThat(user.getOrgId()).isEqualTo("org-1");
        assertThat(user.getSubjectType()).isEqualTo("app");
    }

    @Test
    void checksRoleAndPermissionBySubject() {
        LoginContext.bind(LoginUser.builder()
                .userId("u1")
                .subjectType("admin")
                .roles(List.of("manager"))
                .permissions(List.of("cms:user:update"))
                .build());

        assertThat(LoginContext.currentSubjectType()).contains("admin");
        assertThat(LoginContext.hasRole("admin", "manager")).isTrue();
        assertThat(LoginContext.hasPermission("admin", "cms:user:update")).isTrue();
        assertThat(LoginContext.hasPermission("app", "cms:user:update")).isFalse();
    }
}
