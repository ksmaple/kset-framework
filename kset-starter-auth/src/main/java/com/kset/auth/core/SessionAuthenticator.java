package com.kset.auth.core;

import com.kset.auth.session.LoginSessionStore;
import com.kset.auth.spi.Authenticator;
import com.kset.common.auth.LoginUser;

import java.util.Optional;

public class SessionAuthenticator implements Authenticator {

    private final LoginSessionStore sessionStore;

    public SessionAuthenticator(LoginSessionStore sessionStore) {
        this.sessionStore = sessionStore;
    }

    @Override
    public String scheme() {
        return AuthSchemes.SESSION;
    }

    @Override
    public AuthResult authenticate(AuthRequest request, AuthRuleMatch match) {
        String token = request.header(match.getTokenHeader());
        if (!hasText(token)) {
            return AuthResult.failure(401, "未登录");
        }
        Optional<LoginUser> user = sessionStore.findByToken(token);
        return user.map(loginUser -> AuthResult.authenticated(loginUser
                        .withToken(token)
                        .withSubjectType(match.getSubject())))
                .orElseGet(() -> AuthResult.failure(401, "未登录"));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
