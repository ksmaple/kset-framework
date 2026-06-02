package com.kset.auth.spi;

import com.kset.auth.core.AuthRequest;
import com.kset.auth.core.AuthResult;
import com.kset.auth.core.AuthRuleMatch;

public interface Authenticator {

    String scheme();

    AuthResult authenticate(AuthRequest request, AuthRuleMatch match);
}
