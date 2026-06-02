package com.kset.common.context;

public final class KsetContextScope implements AutoCloseable {

    private final KsetContextSnapshot previous;
    private boolean closed;

    KsetContextScope(KsetContextSnapshot previous) {
        this.previous = previous;
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            KsetContext.restore(previous);
        }
    }
}
