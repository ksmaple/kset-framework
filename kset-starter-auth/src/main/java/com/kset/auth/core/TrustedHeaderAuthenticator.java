package com.kset.auth.core;

import com.kset.auth.spi.Authenticator;
import com.kset.auth.spi.LoginUserHeaderCodec;

public class TrustedHeaderAuthenticator implements Authenticator {

    private final LoginUserHeaderCodec headerCodec;

    public TrustedHeaderAuthenticator(LoginUserHeaderCodec headerCodec) {
        this.headerCodec = headerCodec;
    }

    @Override
    public String scheme() {
        return AuthSchemes.TRUSTED_HEADER;
    }

    @Override
    public AuthResult authenticate(AuthRequest request, AuthRuleMatch match) {
        return headerCodec.decode(request.getHeaderReader(), match.getSubject(), match.getTrustedHeaderName())
                .map(user -> AuthResult.authenticated(user.withSubjectType(match.getSubject())))
                .orElseGet(() -> AuthResult.failure(401, "未登录"));
    }
}
