package com.kset.auth;

import com.kset.auth.web.AuthExceptionHandler;
import com.kset.common.auth.LoginRequiredException;
import com.kset.common.auth.PermissionDeniedException;
import com.kset.web.config.KsetWebProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class AuthExceptionHandlerTest {

    @Test
    void usesRealHttpStatusWhenConfigured() {
        KsetWebProperties properties = new KsetWebProperties();
        properties.getExceptionHandling().setUseHttpStatus(true);
        AuthExceptionHandler handler = new AuthExceptionHandler(properties);

        assertThat(handler.handleLoginRequired(new LoginRequiredException("未登录")).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(handler.handlePermissionDenied(new PermissionDeniedException("无权限")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }
}
