package com.kset.common.event;

/**
 * Asynchronous event send callback.
 */
public interface SendCallback {

    /**
     * Called when an event is published successfully.
     */
    void onSuccess();

    /**
     * Called when event publishing fails.
     *
     * @param throwable failure
     */
    void onException(Throwable throwable);
}
