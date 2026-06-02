package com.kset.common.auth;

public final class LoginContextSnapshot {

    private final LoginUser user;

    public LoginContextSnapshot(LoginUser user) {
        this.user = user;
    }

    public LoginUser getUser() {
        return user;
    }
}
