package com.kset.common.event;

/**
 * Event consumption facade.
 *
 * @param <T> event type
 */
public interface EventHandler<T> {

    /**
     * Event type handled by this handler.
     *
     * @return event class
     */
    Class<T> eventType();

    /**
     * Handle an event.
     *
     * @param event event payload
     */
    void handle(T event);
}
