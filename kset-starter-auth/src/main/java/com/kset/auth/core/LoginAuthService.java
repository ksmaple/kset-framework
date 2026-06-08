package com.kset.auth.core;

import com.kset.auth.session.LoginSessionStore;
import com.kset.auth.spi.Authenticator;
import com.kset.common.auth.LoginUser;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class LoginAuthService {

    private final LoginSessionStore sessionStore;
    private final AuthRuleResolver ruleResolver;
    private final Map<String, Authenticator> authenticators;

    public LoginAuthService(LoginSessionStore sessionStore) {
        this(sessionStore, null, List.of());
    }

    public LoginAuthService(LoginSessionStore sessionStore,
                            AuthRuleResolver ruleResolver,
                            List<Authenticator> authenticators) {
        this.sessionStore = sessionStore;
        this.ruleResolver = ruleResolver;
        this.authenticators = authenticators.stream()
                .collect(Collectors.toMap(authenticator -> normalize(authenticator.scheme()),
                        Function.identity(),
                        (left, right) -> left));
    }

    public Optional<LoginUser> authenticate(String token) {
        if (sessionStore == null) {
            return Optional.empty();
        }
        return sessionStore.findByToken(token);
    }

    public AuthRuleMatch resolve(AuthRequest request) {
        return ruleResolver != null ? ruleResolver.resolve(request) : null;
    }

    public AuthResult authenticate(AuthRequest request) {
        return authenticate(request, resolve(request));
    }

    public AuthResult authenticate(AuthRequest request, AuthRuleMatch match) {
        if (match == null) {
            return AuthResult.failure(401, "未登录");
        }
        Authenticator authenticator = authenticators.get(normalize(match.getScheme()));
        if (authenticator == null) {
            return AuthResult.failure(401, "未登录");
        }
        return authenticator.authenticate(request, match);
    }

    private static String normalize(String value) {
        return value != null && !value.isBlank() ? value.trim().toLowerCase() : "";
    }
}
