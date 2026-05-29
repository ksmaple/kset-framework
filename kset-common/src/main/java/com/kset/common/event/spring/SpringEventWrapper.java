package com.kset.common.event.spring;

/**
 * Standard event wrapper for local Spring implementation.
 */
public final class SpringEventWrapper {

    private final Object payload;

    public SpringEventWrapper(Object payload) {
        this.payload = payload;
    }

    public Object getPayload() {
        return payload;
    }
}
