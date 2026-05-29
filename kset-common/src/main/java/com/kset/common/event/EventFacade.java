package com.kset.common.event;

/**
 * Event publishing facade.
 */
public interface EventFacade {

    /**
     * Publish an event synchronously.
     *
     * @param event event payload
     */
    void publish(Object event);

    /**
     * Publish an event asynchronously.
     *
     * @param event event payload
     * @param callback send callback, nullable
     */
    void publishAsync(Object event, SendCallback callback);

    /**
     * Publish an event after the given delay.
     *
     * @param event event payload
     * @param delayMillis delay in milliseconds
     */
    void publishDelay(Object event, long delayMillis);

    /**
     * Publish an ordered event with a hash key.
     *
     * @param event event payload
     * @param hashKey ordering hash key
     */
    void publishOrderly(Object event, String hashKey);

    /**
     * Publish an event after the current Spring transaction commits.
     *
     * @param event event payload
     */
    void publishTransaction(Object event);
}
