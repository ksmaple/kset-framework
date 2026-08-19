package com.kset.common.event;

/**
 * 事件发布门面。
 *
 * <p>未引入 MQ 时走进程内 Spring 事件；引入 {@code kset-starter-mq} 后同一套 API 切到 RocketMQ。
 * 本地 {@code publishAsync} / {@code publishDelay} 会带上调用线程的登录态和 Trace。
 * 用法见 {@code docs/usage/events.md}。
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
     * <p>Local Spring implementation restores the calling thread's {@code KsetContext} and trace
     * on the async thread.
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
    default void publishDelay(Object event, long delayMillis) {
        publishDelay(event, delayMillis, null);
    }

    /**
     * Publish an event after the given delay.
     *
     * <p>{@code callback} is invoked when the delayed publish completes or fails.
     * Local Spring delivery happens after the delay and restores the calling thread's
     * {@code KsetContext} and trace. RocketMQ invokes it when the delay message is accepted.
     *
     * @param event event payload
     * @param delayMillis delay in milliseconds
     * @param callback send callback, nullable
     */
    void publishDelay(Object event, long delayMillis, SendCallback callback);

    /**
     * Publish an ordered event with a hash key.
     *
     * <p>Same {@code hashKey} is serialized on the local Spring implementation.
     * Cross-process ordering requires {@code kset-starter-mq}.
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
