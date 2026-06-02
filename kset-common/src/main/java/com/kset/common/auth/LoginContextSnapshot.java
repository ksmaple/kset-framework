package com.kset.common.auth;

import com.kset.common.context.KsetContextKeys;
import com.kset.common.context.KsetContextSnapshot;

public final class LoginContextSnapshot {

    private final KsetContextSnapshot snapshot;

    public LoginContextSnapshot(LoginUser user) {
        this(new KsetContextSnapshot(user != null ? java.util.Map.of(KsetContextKeys.LOGIN_USER.getName(), user) : java.util.Map.of()));
    }

    public LoginContextSnapshot(KsetContextSnapshot snapshot) {
        this.snapshot = snapshot != null ? snapshot : new KsetContextSnapshot(java.util.Map.of());
    }

    public LoginUser getUser() {
        return snapshot.get(KsetContextKeys.LOGIN_USER).orElse(null);
    }

    public KsetContextSnapshot getSnapshot() {
        return snapshot;
    }
}
