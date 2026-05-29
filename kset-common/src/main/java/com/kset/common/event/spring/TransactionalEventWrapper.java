package com.kset.common.event.spring;

/**
 * Transactional event wrapper for local Spring implementation.
 */
public final class TransactionalEventWrapper {

    private final Object payload;

    public TransactionalEventWrapper(Object payload) {
        this.payload = payload;
    }

    public Object getPayload() {
        return payload;
    }
}
