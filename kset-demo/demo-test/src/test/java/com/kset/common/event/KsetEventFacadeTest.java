package com.kset.common.event;

import com.kset.common.event.autoconfigure.KsetEventAutoConfiguration;
import com.kset.common.monitor.DubboAttachmentAccessor;
import com.kset.common.monitor.GatewayTraceBinding;
import com.kset.common.monitor.HttpTraceBinding;
import com.kset.common.monitor.Monitor;
import com.kset.common.monitor.TraceSnapshot;
import com.kset.common.monitor.facade.MetricKind;
import com.kset.common.monitor.facade.MonitorFacade;
import com.kset.common.monitor.facade.MonitorStatus;
import com.kset.common.monitor.facade.MonitorTransaction;
import com.kset.common.monitor.internal.NoOpMonitorFacade;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class KsetEventFacadeTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestEventConfiguration.class)
            .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(
                    org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration.class,
                    KsetEventAutoConfiguration.class));

    @AfterEach
    void resetMonitor() {
        Monitor.install(new NoOpMonitorFacade());
    }

    @Test
    void publishDispatchesToMatchingHandler() {
        contextRunner.run(context -> {
            EventFacade eventFacade = context.getBean(EventFacade.class);
            TestEventHandler handler = context.getBean(TestEventHandler.class);

            eventFacade.publish(new TestEvent("sync"));

            assertThat(handler.values()).containsExactly("sync");
        });
    }

    @Test
    void publishAsyncInvokesCallback() {
        contextRunner.run(context -> {
            EventFacade eventFacade = context.getBean(EventFacade.class);
            TestEventHandler handler = context.getBean(TestEventHandler.class);
            CountDownLatch latch = new CountDownLatch(1);

            eventFacade.publishAsync(new TestEvent("async"), new SendCallback() {
                @Override
                public void onSuccess() {
                    latch.countDown();
                }

                @Override
                public void onException(Throwable throwable) {
                }
            });

            assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(handler.values()).containsExactly("async");
        });
    }

    @Test
    void publishDelayDispatchesLater() {
        contextRunner.run(context -> {
            EventFacade eventFacade = context.getBean(EventFacade.class);
            TestEventHandler handler = context.getBean(TestEventHandler.class);

            eventFacade.publishDelay(new TestEvent("delay"), 20);

            assertThat(handler.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(handler.values()).containsExactly("delay");
        });
    }

    @Test
    void publishOrderlyDispatchesPayload() {
        contextRunner.run(context -> {
            EventFacade eventFacade = context.getBean(EventFacade.class);
            TestEventHandler handler = context.getBean(TestEventHandler.class);

            eventFacade.publishOrderly(new TestEvent("orderly"), "user-1");

            assertThat(handler.values()).containsExactly("orderly");
        });
    }

    @Test
    void publishTransactionDispatchesAfterCommit() {
        contextRunner.run(context -> {
            EventFacade eventFacade = context.getBean(EventFacade.class);
            TestEventHandler handler = context.getBean(TestEventHandler.class);
            TransactionTemplate transactionTemplate = context.getBean(TransactionTemplate.class);

            transactionTemplate.executeWithoutResult(status -> {
                eventFacade.publishTransaction(new TestEvent("tx"));
                assertThat(handler.values()).isEmpty();
            });

            assertThat(handler.values()).containsExactly("tx");
        });
    }

    @Test
    void publishAndConsumeCreateMonitorTransactions() {
        CollectingMonitorFacade monitorFacade = new CollectingMonitorFacade();
        Monitor.install(monitorFacade);

        contextRunner.run(context -> {
            EventFacade eventFacade = context.getBean(EventFacade.class);

            eventFacade.publish(new TestEvent("monitor"));

            assertThat(monitorFacade.transactionNames())
                    .contains("event.publish.sync", "event.consume");
        });
    }

    record TestEvent(String value) {
    }

    static class TestEventHandler implements EventHandler<TestEvent> {

        private final List<String> values = new CopyOnWriteArrayList<>();
        private final CountDownLatch latch = new CountDownLatch(1);

        @Override
        public Class<TestEvent> eventType() {
            return TestEvent.class;
        }

        @Override
        public void handle(TestEvent event) {
            values.add(event.value());
            latch.countDown();
        }

        List<String> values() {
            return values;
        }

        boolean await(long timeout, TimeUnit unit) throws InterruptedException {
            return latch.await(timeout, unit);
        }
    }

    static class CollectingMonitorFacade implements MonitorFacade {

        private final NoOpMonitorFacade delegate = new NoOpMonitorFacade();
        private final List<String> transactionNames = new CopyOnWriteArrayList<>();

        @Override
        public Optional<String> currentTraceId() {
            return delegate.currentTraceId();
        }

        @Override
        public Optional<String> currentSpanId() {
            return delegate.currentSpanId();
        }

        @Override
        public Optional<String> currentGrayTag() {
            return delegate.currentGrayTag();
        }

        @Override
        public String generateTraceId() {
            return UUID.randomUUID().toString().replace("-", "");
        }

        @Override
        public String generateSpanId() {
            return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }

        @Override
        public HttpTraceBinding bindHttpIncoming(String incomingTraceId) {
            return delegate.bindHttpIncoming(incomingTraceId);
        }

        @Override
        public void bindHttpGrayTag(String incomingGrayTag, String defaultGray) {
            delegate.bindHttpGrayTag(incomingGrayTag, defaultGray);
        }

        @Override
        public void clearHttpGrayTag() {
            delegate.clearHttpGrayTag();
        }

        @Override
        public void bindDubboConsumer(DubboAttachmentAccessor attachments, String defaultGray) {
            delegate.bindDubboConsumer(attachments, defaultGray);
        }

        @Override
        public void bindDubboProvider(DubboAttachmentAccessor attachments, String defaultGray) {
            delegate.bindDubboProvider(attachments, defaultGray);
        }

        @Override
        public GatewayTraceBinding resolveGatewayTrace(String incomingTraceId, String traceHeaderName) {
            return delegate.resolveGatewayTrace(incomingTraceId, traceHeaderName);
        }

        @Override
        public Object putReactorContext(Object context, String traceId, String grayTag) {
            return delegate.putReactorContext(context, traceId, grayTag);
        }

        @Override
        public Optional<String> getFromReactor(Object contextView, String key) {
            return delegate.getFromReactor(contextView, key);
        }

        @Override
        public void setTraceId(String traceId) {
            delegate.setTraceId(traceId);
        }

        @Override
        public void setSpanId(String spanId) {
            delegate.setSpanId(spanId);
        }

        @Override
        public void setGrayTag(String grayTag) {
            delegate.setGrayTag(grayTag);
        }

        @Override
        public void clear() {
            delegate.clear();
        }

        @Override
        public TraceSnapshot capture() {
            return delegate.capture();
        }

        @Override
        public void restore(TraceSnapshot snapshot) {
            delegate.restore(snapshot);
        }

        @Override
        public MonitorTransaction newTransaction(String type, String name) {
            transactionNames.add(name);
            return new CollectingMonitorTransaction(type, name);
        }

        @Override
        public void logEvent(String type, String name, MonitorStatus status, String data) {
            delegate.logEvent(type, name, status, data);
        }

        @Override
        public void logMetric(String name, long value, MetricKind kind) {
            delegate.logMetric(name, value, kind);
        }

        @Override
        public void logError(Throwable throwable, String message) {
            delegate.logError(throwable, message);
        }

        List<String> transactionNames() {
            return transactionNames;
        }
    }

    static class CollectingMonitorTransaction implements MonitorTransaction {

        private final String type;
        private final String name;

        CollectingMonitorTransaction(String type, String name) {
            this.type = type;
            this.name = name;
        }

        @Override
        public void setStatus(MonitorStatus status) {
        }

        @Override
        public void setStatus(Throwable throwable) {
        }

        @Override
        public void addData(String key, String value) {
        }

        @Override
        public String getType() {
            return type;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public void close() {
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class TestEventConfiguration {

        @Bean
        TestEventHandler testEventHandler() {
            return new TestEventHandler();
        }

        @Bean
        PlatformTransactionManager platformTransactionManager() {
            return new AbstractPlatformTransactionManager() {
                @Override
                protected Object doGetTransaction() {
                    return new Object();
                }

                @Override
                protected void doBegin(Object transaction, TransactionDefinition definition) {
                }

                @Override
                protected void doCommit(DefaultTransactionStatus status) {
                }

                @Override
                protected void doRollback(DefaultTransactionStatus status) {
                }
            };
        }
    }
}
