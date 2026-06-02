package com.kset.auth.session;

import com.kset.auth.config.KsetAuthProperties;
import com.kset.common.auth.LoginUser;
import com.kset.redis.core.KsetRedisService;

import java.time.Duration;
import java.util.Optional;

public class RedisLoginSessionStore implements LoginSessionStore {

    private final KsetRedisService redisService;
    private final KsetAuthProperties properties;

    public RedisLoginSessionStore(KsetRedisService redisService, KsetAuthProperties properties) {
        this.redisService = redisService;
        this.properties = properties;
    }

    @Override
    public Optional<LoginUser> findByToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        LoginUser user = redisService.get(sessionKey(token), LoginUser.class);
        if (user != null && properties.getSession().isSlidingRefreshEnabled()) {
            refreshIfNeeded(token);
        }
        return Optional.ofNullable(user != null ? user.withToken(token) : null);
    }

    @Override
    public void save(String token, LoginUser user, Duration ttl) {
        if (token == null || token.isBlank() || user == null) {
            return;
        }
        redisService.setEx(sessionKey(token), user.withToken(token), effectiveTtl(ttl));
    }

    @Override
    public void delete(String token) {
        if (token != null && !token.isBlank()) {
            redisService.delete(sessionKey(token));
        }
    }

    @Override
    public void refresh(String token, Duration ttl) {
        if (token != null && !token.isBlank()) {
            redisService.expire(sessionKey(token), effectiveTtl(ttl));
        }
    }

    private void refreshIfNeeded(String token) {
        Long seconds = redisService.ttl(sessionKey(token));
        Duration threshold = properties.getSession().getRefreshThreshold();
        if (seconds != null && seconds > 0 && threshold != null && seconds <= threshold.toSeconds()) {
            refresh(token, properties.getSession().getTtl());
        }
    }

    private Duration effectiveTtl(Duration ttl) {
        return ttl != null ? ttl : properties.getSession().getTtl();
    }

    private String sessionKey(String token) {
        return properties.getSession().getKeyPrefix() + token;
    }
}
