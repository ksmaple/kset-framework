package com.kset.auth.core;

import com.kset.common.auth.LoginUser;

import java.util.Optional;

public final class AuthResult {

    private final boolean authenticated;
    private final boolean permitAll;
    private final LoginUser user;
    private final int code;
    private final String message;

    private AuthResult(boolean authenticated, boolean permitAll, LoginUser user, int code, String message) {
        this.authenticated = authenticated;
        this.permitAll = permitAll;
        this.user = user;
        this.code = code;
        this.message = message;
    }

    public static AuthResult authenticated(LoginUser user) {
        return new AuthResult(true, false, user, 0, null);
    }

    public static AuthResult permitAll() {
        return new AuthResult(false, true, null, 0, null);
    }

    public static AuthResult failure(int code, String message) {
        return new AuthResult(false, false, null, code, message);
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public boolean isPermitAll() {
        return permitAll;
    }

    public Optional<LoginUser> getUser() {
        return Optional.ofNullable(user);
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
