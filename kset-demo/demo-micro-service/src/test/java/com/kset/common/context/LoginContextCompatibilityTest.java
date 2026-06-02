package com.kset.common.context;

import com.kset.common.auth.LoginContext;
import com.kset.common.auth.LoginContextScope;
import com.kset.common.auth.LoginContextSnapshot;
import com.kset.common.auth.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LoginContextCompatibilityTest {

    @AfterEach
    void clear() {
        KsetContext.clear();
    }

    @Test
    void loginContextUsesKsetContext() {
        LoginUser user = LoginUser.builder()
                .userId("u1")
                .subjectType("admin")
                .roles(List.of("manager"))
                .permissions(List.of("cms:user:update"))
                .build();

        LoginContext.bind(user);

        assertThat(KsetContext.get(KsetContextKeys.LOGIN_USER)).contains(user);
        assertThat(LoginContext.currentUserId()).contains("u1");
        assertThat(LoginContext.currentSubjectType()).contains("admin");
        assertThat(LoginContext.hasRole("admin", "manager")).isTrue();
        assertThat(LoginContext.hasPermission("admin", "cms:user:update")).isTrue();
    }

    @Test
    void loginSnapshotRestoresOnlyUserContext() {
        LoginContext.bind(LoginUser.builder().userId("u1").build());
        KsetContext.put(KsetContextKeys.LANGUAGE, "zh-CN");
        LoginContextSnapshot snapshot = LoginContext.capture();

        LoginContext.bind(LoginUser.builder().userId("u2").build());
        KsetContext.put(KsetContextKeys.LANGUAGE, "en-US");

        LoginContext.restore(snapshot);

        assertThat(LoginContext.currentUserId()).contains("u1");
        assertThat(KsetContext.get(KsetContextKeys.LANGUAGE)).contains("en-US");
    }

    @Test
    void loginScopeRestoresPreviousSnapshot() {
        LoginContext.bind(LoginUser.builder().userId("u1").build());
        LoginContextSnapshot snapshot = new LoginContextSnapshot(LoginUser.builder().userId("u2").build());

        try (LoginContextScope ignored = LoginContext.openScope(snapshot)) {
            assertThat(LoginContext.currentUserId()).contains("u2");
        }

        assertThat(LoginContext.currentUserId()).contains("u1");
    }
}
