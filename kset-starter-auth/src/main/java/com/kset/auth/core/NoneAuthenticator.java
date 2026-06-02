package com.kset.auth.core;

import com.kset.auth.spi.Authenticator;

public class NoneAuthenticator implements Authenticator {

    @Override
    public String scheme() {
        return AuthSchemes.NONE;
    }

    @Override
    public AuthResult authenticate(AuthRequest request, AuthRuleMatch match) {
        return AuthResult.permitAll();
    }
}
