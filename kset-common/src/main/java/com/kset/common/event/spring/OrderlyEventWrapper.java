package com.kset.common.event.spring;

/**
 * Ordered event wrapper for local Spring implementation.
 */
public final class OrderlyEventWrapper {

    private final Object payload;
    private final String hashKey;

    public OrderlyEventWrapper(Object payload, String hashKey) {
        this.payload = payload;
        this.hashKey = hashKey;
    }

    public Object getPayload() {
        return payload;
    }

    public String getHashKey() {
        return hashKey;
    }
}
