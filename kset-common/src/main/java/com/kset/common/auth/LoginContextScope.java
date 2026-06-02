package com.kset.common.auth;

public final class LoginContextScope implements AutoCloseable {

    private final LoginContextSnapshot previous;
    private boolean closed;

    LoginContextScope(LoginContextSnapshot previous) {
        this.previous = previous;
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            LoginContext.restore(previous);
        }
    }
}
