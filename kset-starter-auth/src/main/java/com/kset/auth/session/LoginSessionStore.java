package com.kset.auth.session;

import com.kset.common.auth.LoginUser;

import java.time.Duration;
import java.util.Optional;

public interface LoginSessionStore {

    Optional<LoginUser> findByToken(String token);

    void save(String token, LoginUser user, Duration ttl);

    void delete(String token);

    void refresh(String token, Duration ttl);
}
