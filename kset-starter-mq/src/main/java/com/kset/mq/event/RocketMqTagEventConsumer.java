package com.kset.mq.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kset.common.monitor.Monitor;
import com.kset.common.monitor.MonitorScope;
import com.kset.common.monitor.TraceSnapshot;
import com.kset.common.monitor.facade.MonitorTransaction;
import com.kset.common.trace.TraceHeaders;
import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.apache.rocketmq.client.core.RocketMQListener;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Objects;

/**
 * One business subscription per listener and consumer group. Subclasses declare their
 * Topic, Tag and unique consumer group with {@code @RocketMQMessageListener}.
 */
public abstract class RocketMqTagEventConsumer<T> implements RocketMQListener {

    private final Class<T> eventType;
    private final ObjectMapper objectMapper;

    protected RocketMqTagEventConsumer(Class<T> eventType, ObjectMapper objectMapper) {
        this.eventType = Objects.requireNonNull(eventType, "eventType must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public final ConsumeResult consume(MessageView messageView) {
        String actualType = messageView.getProperties().get(RocketMqEventHeaders.EVENT_TYPE);
        try (MonitorScope ignored = Monitor.openScope(traceSnapshot(messageView))) {
            MonitorTransaction transaction = RocketMqEventMonitorSupport.beginConsume(eventType.getName(), null);
            RocketMqEventMonitorSupport.addData(transaction, "handler", getClass().getName());
            try {
                if (!eventType.getName().equals(actualType)) {
                    throw new IllegalArgumentException("unexpected rocketmq event type: " + actualType);
                }
                T event = objectMapper.readValue(bytes(messageView.getBody()), eventType);
                handle(event);
                RocketMqEventMonitorSupport.success(transaction);
                return ConsumeResult.SUCCESS;
            } catch (Throwable throwable) {
                RocketMqEventMonitorSupport.fail(transaction, throwable, "consume", eventType.getName());
                return ConsumeResult.FAILURE;
            } finally {
                RocketMqEventMonitorSupport.close(transaction);
            }
        }
    }

    protected abstract void handle(T event);

    private TraceSnapshot traceSnapshot(MessageView messageView) {
        Map<String, String> properties = messageView.getProperties();
        String traceId = properties.get(TraceHeaders.TRACE_ID_KEY);
        if (traceId == null || traceId.isBlank()) {
            traceId = Monitor.generateTraceId();
        }
        String spanId = properties.get(TraceHeaders.SPAN_ID_KEY);
        if (spanId == null || spanId.isBlank()) {
            spanId = Monitor.generateSpanId();
        }
        return new TraceSnapshot(traceId, spanId, properties.get(TraceHeaders.GRAY_TAG_KEY));
    }

    private byte[] bytes(ByteBuffer buffer) {
        ByteBuffer duplicate = buffer.asReadOnlyBuffer();
        byte[] bytes = new byte[duplicate.remaining()];
        duplicate.get(bytes);
        return bytes;
    }
}
