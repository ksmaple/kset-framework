package com.kset.mq.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kset.common.monitor.Monitor;
import com.kset.common.trace.TraceHeaders;
import org.apache.rocketmq.client.annotation.RocketMQMessageListener;
import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RocketMqTagEventConsumerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void failureDoesNotChangeOtherSubscriptionResult() throws Exception {
        RecordingConsumer successful = new RecordingConsumer(objectMapper);
        FailingConsumer failed = new FailingConsumer(objectMapper);
        MessageView message = message(TestEvent.class.getName());

        assertThat(failed.consume(message)).isEqualTo(ConsumeResult.FAILURE);
        assertThat(successful.consume(message)).isEqualTo(ConsumeResult.SUCCESS);
        assertThat(successful.values).containsExactly("created");
        assertThat(successful.traceIds).containsExactly("trace-created");
        assertThat(RecordingConsumer.class.getAnnotation(RocketMQMessageListener.class).consumerGroup())
                .isNotEqualTo(FailingConsumer.class.getAnnotation(RocketMQMessageListener.class).consumerGroup());
    }

    @Test
    void wrongEventTypeFailsThisSubscription() throws Exception {
        RecordingConsumer consumer = new RecordingConsumer(objectMapper);

        assertThat(consumer.consume(message(String.class.getName()))).isEqualTo(ConsumeResult.FAILURE);
        assertThat(consumer.values).isEmpty();
    }

    private MessageView message(String eventType) throws Exception {
        MessageView message = mock(MessageView.class);
        when(message.getProperties()).thenReturn(Map.of(
                RocketMqEventHeaders.EVENT_TYPE, eventType,
                TraceHeaders.TRACE_ID_KEY, "trace-created"));
        when(message.getBody()).thenReturn(ByteBuffer.wrap(objectMapper.writeValueAsBytes(new TestEvent("created"))));
        return message;
    }

    record TestEvent(String value) {
    }

    @RocketMQMessageListener(endpoints = "${rocketmq.producer.endpoints:}", topic = "order-event",
            tag = "ORDER_CREATED", consumerGroup = "order-audit-consumer")
    static class RecordingConsumer extends RocketMqTagEventConsumer<TestEvent> {
        private final List<String> values = new ArrayList<>();
        private final List<String> traceIds = new ArrayList<>();

        RecordingConsumer(ObjectMapper objectMapper) {
            super(TestEvent.class, objectMapper);
        }

        @Override
        protected void handle(TestEvent event) {
            values.add(event.value());
            traceIds.add(Monitor.currentTraceId().orElse("-"));
        }
    }

    @RocketMQMessageListener(endpoints = "${rocketmq.producer.endpoints:}", topic = "order-event",
            tag = "ORDER_CREATED", consumerGroup = "order-notify-consumer")
    static class FailingConsumer extends RocketMqTagEventConsumer<TestEvent> {
        FailingConsumer(ObjectMapper objectMapper) {
            super(TestEvent.class, objectMapper);
        }

        @Override
        protected void handle(TestEvent event) {
            throw new IllegalStateException("notification unavailable");
        }
    }
}
