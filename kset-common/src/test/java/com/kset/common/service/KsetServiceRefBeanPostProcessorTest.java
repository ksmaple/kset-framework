package com.kset.common.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.QualifierAnnotationAutowireCandidateResolver;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KsetServiceRefBeanPostProcessorTest {

    @Test
    void usesLocalServiceWithoutRemoteAdapter() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        GreetingService local = name -> "local " + name;
        beanFactory.registerSingleton("localGreetingService", local);
        KsetServiceRefBeanPostProcessor processor = processor(beanFactory);
        Consumer consumer = new Consumer();

        processor.postProcessBeforeInitialization(consumer, "consumer");
        processor.validateLocalReferences();

        assertThat(consumer.service).isSameAs(local);
    }

    @Test
    void failsClearlyWithoutLocalServiceOrRemoteAdapter() {
        KsetServiceRefBeanPostProcessor processor = processor(new DefaultListableBeanFactory());

        assertThatThrownBy(() -> processor.postProcessBeforeInitialization(new Consumer(), "consumer"))
                .hasMessageContaining("No local service")
                .hasMessageContaining("no available remote service provider");
    }

    @Test
    void prefersLocalServiceWhenRemoteAdapterIsAvailable() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        GreetingService local = name -> "local " + name;
        StubRemoteProvider remote = new StubRemoteProvider((GreetingService) name -> "remote " + name);
        beanFactory.registerSingleton("localGreetingService", local);
        beanFactory.registerSingleton("remoteProvider", remote);
        KsetServiceRefBeanPostProcessor processor = processor(beanFactory);
        Consumer consumer = new Consumer();

        processor.postProcessBeforeInitialization(consumer, "consumer");
        processor.validateLocalReferences();

        assertThat(consumer.service).isSameAs(local);
        assertThat(remote.referenceCalls.get()).isZero();
        assertThat(remote.conflictChecks.get()).isEqualTo(1);
    }

    @Test
    void skipsRemoteLogicWhenAdapterIsUnavailable() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        GreetingService local = name -> name;
        StubRemoteProvider remote = new StubRemoteProvider(null);
        remote.available = false;
        beanFactory.registerSingleton("localGreetingService", local);
        beanFactory.registerSingleton("remoteProvider", remote);
        KsetServiceRefBeanPostProcessor processor = processor(beanFactory);

        processor.postProcessBeforeInitialization(new Consumer(), "consumer");
        processor.validateLocalReferences();

        assertThat(remote.conflictChecks.get()).isZero();
    }

    @Test
    void usesRemoteAdapterWhenLocalServiceIsAbsent() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        GreetingService remoteService = name -> "remote " + name;
        StubRemoteProvider remote = new StubRemoteProvider(remoteService);
        beanFactory.registerSingleton("remoteProvider", remote);
        KsetServiceRefBeanPostProcessor processor = processor(beanFactory);
        VersionedConsumer consumer = new VersionedConsumer();

        processor.postProcessBeforeInitialization(consumer, "consumer");

        assertThat(consumer.service).isSameAs(remoteService);
        assertThat(remote.referenceCalls.get()).isEqualTo(1);
        assertThat(remote.requested.group()).isEqualTo("orders");
        assertThat(remote.requested.version()).isEqualTo("1.0");
        processor.validateLocalReferences();
        assertThat(remote.conflictChecks.get()).isZero();
    }

    @Test
    void resolvesRemoteAttributesFromSpringProperties() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.addEmbeddedValueResolver(value -> value
                .replace("${service.group}", "orders")
                .replace("${service.version}", "2.0"));
        StubRemoteProvider remote = new StubRemoteProvider((GreetingService) name -> name);
        beanFactory.registerSingleton("remoteProvider", remote);
        KsetServiceRefBeanPostProcessor processor = processor(beanFactory);

        processor.postProcessBeforeInitialization(new ConfiguredConsumer(), "consumer");

        assertThat(remote.requested.group()).isEqualTo("orders");
        assertThat(remote.requested.version()).isEqualTo("2.0");
    }

    @Test
    void rejectsLocalAndRemoteProviderConflict() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("localGreetingService", (GreetingService) name -> name);
        StubRemoteProvider remote = new StubRemoteProvider(null);
        remote.conflict = true;
        beanFactory.registerSingleton("remoteProvider", remote);
        KsetServiceRefBeanPostProcessor processor = processor(beanFactory);
        processor.postProcessBeforeInitialization(new Consumer(), "consumer");

        assertThatThrownBy(processor::validateLocalReferences)
                .hasMessageContaining("consumer.service")
                .hasMessageContaining(GreetingService.class.getName());
    }

    @Test
    void honorsSpringQualifierForLocalService() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.setAutowireCandidateResolver(new QualifierAnnotationAutowireCandidateResolver());
        beanFactory.registerSingleton("first", (GreetingService) name -> "first");
        GreetingService second = name -> "second";
        beanFactory.registerSingleton("second", second);
        KsetServiceRefBeanPostProcessor processor = processor(beanFactory);
        QualifiedConsumer consumer = new QualifiedConsumer();

        processor.postProcessBeforeInitialization(consumer, "consumer");

        assertThat(consumer.service).isSameAs(second);
    }

    @Test
    void rejectsAmbiguousLocalServices() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("first", (GreetingService) name -> "first");
        beanFactory.registerSingleton("second", (GreetingService) name -> "second");
        KsetServiceRefBeanPostProcessor processor = processor(beanFactory);

        assertThatThrownBy(() -> processor.postProcessBeforeInitialization(new Consumer(), "consumer"))
                .isInstanceOf(NoUniqueBeanDefinitionException.class);
    }

    @Test
    void rejectsNonInterfaceFields() {
        KsetServiceRefBeanPostProcessor processor = processor(new DefaultListableBeanFactory());

        assertThatThrownBy(() -> processor.postProcessBeforeInitialization(new InvalidConsumer(), "consumer"))
                .hasMessageContaining("non-static, non-final interface field");
    }

    private KsetServiceRefBeanPostProcessor processor(DefaultListableBeanFactory beanFactory) {
        KsetServiceRefBeanPostProcessor processor = new KsetServiceRefBeanPostProcessor();
        processor.setBeanFactory(beanFactory);
        return processor;
    }

    private interface GreetingService {
        String greet(String name);
    }

    private static class Consumer {
        @KsetReference
        private GreetingService service;
    }

    private static class VersionedConsumer {
        @KsetReference(group = "orders", version = "1.0")
        private GreetingService service;
    }

    private static class QualifiedConsumer {
        @KsetReference
        @Qualifier("second")
        private GreetingService service;
    }

    private static class ConfiguredConsumer {
        @KsetReference(group = "${service.group}", version = "${service.version}")
        private GreetingService service;
    }

    private static class InvalidConsumer {
        @KsetReference
        private String service;
    }

    private static class StubRemoteProvider implements RemoteServiceReferenceProvider {
        private final Object reference;
        private final AtomicInteger referenceCalls = new AtomicInteger();
        private final AtomicInteger conflictChecks = new AtomicInteger();
        private boolean available = true;
        private boolean conflict;
        private ServiceReferenceKey requested;

        private StubRemoteProvider(Object reference) {
            this.reference = reference;
        }

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public Object getReference(ServiceReferenceKey key) {
            requested = key;
            referenceCalls.incrementAndGet();
            return reference;
        }

        @Override
        public boolean hasOtherProcessProvider(ServiceReferenceKey key) {
            conflictChecks.incrementAndGet();
            return conflict;
        }
    }
}
