package com.kset.mq.event;

import com.kset.common.event.EventFacade;
import com.kset.common.event.EventHandler;
import com.kset.common.event.autoconfigure.KsetEventAutoConfiguration;
import com.kset.common.monitor.Monitor;
import com.kset.common.monitor.backend.LogBackend;
import com.kset.common.monitor.internal.DefaultMonitorFacade;
import com.kset.common.monitor.internal.NoOpMonitorFacade;
import com.kset.common.monitor.reporter.NoOpMetricAggregator;
import com.kset.common.monitor.sampler.RateSampler;
import com.kset.common.trace.TraceHeaders;
import com.kset.mq.autoconfigure.KsetRocketMqEventAutoConfiguration;
import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.message.MessageId;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.apache.rocketmq.client.apis.producer.SendReceipt;
import org.apache.rocketmq.client.autoconfigure.RocketMQProperties;
import org.apache.rocketmq.client.core.RocketMQClientTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class KsetRocketMqEventFacadeTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestRocketMqConfiguration.class)
            .withPropertyValues(
                    "spring.application.name=rocket-demo",
                    "rocketmq.producer.endpoints=127.0.0.1:8081",
                    "rocketmq.producer.topic=rocket-demo-topic")
            .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(
                    KsetRocketMqEventAutoConfiguration.class,
                    KsetEventAutoConfiguration.class));

    @BeforeEach
    void installMonitor() {
        Monitor.install(new DefaultMonitorFacade(new LogBackend(), new RateSampler(1.0), new NoOpMetricAggregator()));
        Monitor.clear();
    }

    @AfterEach
    void resetMonitor() {
        Monitor.clear();
        Monitor.install(new NoOpMonitorFacade());
    }

    @Test
    void rocketMqEventFacadeOverridesSpringEventFacade() {
        contextRunner.run(context -> {
            EventFacade eventFacade = context.getBean(EventFacade.class);
            RecordingRocketMqClientTemplate template = context.getBean(RecordingRocketMqClientTemplate.class);

            eventFacade.publish(new TestEvent("sync"));

            assertThat(eventFacade).isInstanceOf(RocketMqEventFacade.class);
            assertThat(template.destination()).isEqualTo("annotated-topic:created");
            assertThat(template.message().getHeaders().get(RocketMqEventHeaders.EVENT_TYPE))
                    .isEqualTo(TestEvent.class.getName());
        });
    }

    @Test
    void rocketMqEventFacadePropagatesMonitorTraceHeaders() {
        contextRunner.run(context -> {
            EventFacade eventFacade = context.getBean(EventFacade.class);
            RecordingRocketMqClientTemplate template = context.getBean(RecordingRocketMqClientTemplate.class);
            Monitor.setTraceId("trace-mq");
            Monitor.setSpanId("span-mq");
            Monitor.setGrayTag("gray-mq");

            eventFacade.publish(new PlainEvent("trace"));

            assertThat(template.message().getHeaders().get(TraceHeaders.TRACE_ID_KEY)).isEqualTo("trace-mq");
            assertThat(template.message().getHeaders().get(TraceHeaders.SPAN_ID_KEY)).isEqualTo("span-mq");
            assertThat(template.message().getHeaders().get(TraceHeaders.GRAY_TAG_KEY)).isEqualTo("gray-mq");
        });
    }

    @Test
    void rocketMqEventOperationsPublishesToProgrammaticDestination() {
        contextRunner.run(context -> {
            RocketMqEventOperations operations = context.getBean(RocketMqEventOperations.class);
            RecordingRocketMqClientTemplate template = context.getBean(RecordingRocketMqClientTemplate.class);

            operations.publish("manual-topic", "manual-tag", new PlainEvent("manual"));

            assertThat(template.destination()).isEqualTo("manual-topic:manual-tag");
        });
    }

    @Test
    void rocketMqEventOperationsCanOmitTopic() {
        contextRunner.run(context -> {
            RocketMqEventOperations operations = context.getBean(RocketMqEventOperations.class);
            RecordingRocketMqClientTemplate template = context.getBean(RecordingRocketMqClientTemplate.class);

            operations.publish(new PlainEvent("default-topic"));

            assertThat(template.destination()).isEqualTo("rocket-demo-topic");
        });
    }

    @Test
    void rocketMqConsumerDispatchesToEventHandler() throws Exception {
        TestEventHandler handler = new TestEventHandler();
        RocketMqEventConsumer consumer = new RocketMqEventConsumer(List.of(handler),
                new com.fasterxml.jackson.databind.ObjectMapper());

        ConsumeResult result = consumer.consume(new TestMessageView(new TestEvent("consume")));

        assertThat(result).isEqualTo(ConsumeResult.SUCCESS);
        assertThat(handler.values()).containsExactly("consume");
        assertThat(handler.traceIds()).containsExactly("trace-consume");
    }

    @KsetMqEvent(topic = "annotated-topic", tag = "created")
    record TestEvent(String value) {
    }

    record PlainEvent(String value) {
    }

    static class TestEventHandler implements EventHandler<TestEvent> {

        private final List<String> values = new CopyOnWriteArrayList<>();
        private final List<String> traceIds = new CopyOnWriteArrayList<>();

        @Override
        public Class<TestEvent> eventType() {
            return TestEvent.class;
        }

        @Override
        public void handle(TestEvent event) {
            values.add(event.value());
            traceIds.add(Monitor.currentTraceId().orElse("-"));
        }

        List<String> values() {
            return values;
        }

        List<String> traceIds() {
            return traceIds;
        }
    }

    static class RecordingRocketMqClientTemplate extends RocketMQClientTemplate {

        private String destination;
        private Message<?> message;

        @Override
        public SendReceipt syncSendNormalMessage(String destination, Message<?> message) {
            this.destination = destination;
            this.message = message;
            return () -> new TestMessageId();
        }

        String destination() {
            return destination;
        }

        Message<?> message() {
            return message;
        }
    }

    static class TestMessageId implements MessageId {

        @Override
        public String getVersion() {
            return "test";
        }

        @Override
        public String toString() {
            return "test-message-id";
        }
    }

    static class TestMessageView implements MessageView {

        private final byte[] body;

        TestMessageView(TestEvent event) throws Exception {
            this.body = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(event);
        }

        @Override
        public MessageId getMessageId() {
            return new TestMessageId();
        }

        @Override
        public String getTopic() {
            return "rocket-demo-topic";
        }

        @Override
        public ByteBuffer getBody() {
            return ByteBuffer.wrap(body);
        }

        @Override
        public Map<String, String> getProperties() {
            return Map.of(
                    RocketMqEventHeaders.EVENT_TYPE, TestEvent.class.getName(),
                    TraceHeaders.TRACE_ID_KEY, "trace-consume",
                    TraceHeaders.SPAN_ID_KEY, "span-consume",
                    TraceHeaders.GRAY_TAG_KEY, "gray-consume");
        }

        @Override
        public Optional<String> getTag() {
            return Optional.empty();
        }

        @Override
        public Collection<String> getKeys() {
            return List.of();
        }

        @Override
        public Optional<String> getMessageGroup() {
            return Optional.empty();
        }

        @Override
        public Optional<Long> getDeliveryTimestamp() {
            return Optional.empty();
        }

        @Override
        public String getBornHost() {
            return "127.0.0.1";
        }

        @Override
        public long getBornTimestamp() {
            return 0;
        }

        @Override
        public int getDeliveryAttempt() {
            return 1;
        }
    }

    @Configuration
    static class TestRocketMqConfiguration {

        @Bean
        RecordingRocketMqClientTemplate rocketMQClientTemplate() {
            return new RecordingRocketMqClientTemplate();
        }

        @Bean
        RocketMQProperties rocketMQProperties() {
            RocketMQProperties properties = new RocketMQProperties();
            RocketMQProperties.Producer producer = new RocketMQProperties.Producer();
            producer.setEndpoints("127.0.0.1:8081");
            producer.setTopic("rocket-demo-topic");
            properties.setProducer(producer);
            return properties;
        }
    }
}
