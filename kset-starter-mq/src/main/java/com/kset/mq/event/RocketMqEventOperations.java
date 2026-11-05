package com.kset.mq.event;

import com.kset.common.event.SendCallback;

public interface RocketMqEventOperations {

    void publish(Object event);

    /** Publish to the given Topic while retaining the event's {@link KsetMqEvent} Tag. */
    void publish(String topic, Object event);

    /** Publish to the given Topic and Tag; an empty Tag explicitly omits the event's annotated Tag. */
    void publish(String topic, String tag, Object event);

    void publishAsync(Object event, SendCallback callback);

    void publishAsync(String topic, String tag, Object event, SendCallback callback);

    void publishDelay(Object event, long delayMillis);

    void publishDelay(String topic, String tag, Object event, long delayMillis);

    void publishOrderly(Object event, String hashKey);

    void publishOrderly(String topic, String tag, Object event, String hashKey);
}
