package com.kset.schedule;

import com.kset.schedule.config.KsetTaskLockAnnotationValidator;
import com.kset.schedule.annotation.KsetTaskLock;
import org.springframework.aop.framework.ProxyFactory;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.stereotype.Component;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

class KsetTaskLockAnnotationValidatorTest {

    @Test
    void passesWhenDurationsValid() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(ValidTask.class)) {
            KsetTaskLockAnnotationValidator validator = new KsetTaskLockAnnotationValidator(context);
            assertThatCode(validator::afterSingletonsInstantiated).doesNotThrowAnyException();
        }
    }

    @Test
    void failsFastWhenDurationInvalid() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(InvalidTask.class)) {
            KsetTaskLockAnnotationValidator validator = new KsetTaskLockAnnotationValidator(context);
            assertThatThrownBy(validator::afterSingletonsInstantiated)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("atMostFor")
                    .hasMessageContaining("InvalidTask");
        }
    }

    @Test
    void validatesLazyBeanWithoutCreatingIt() {
        LazyInvalidTask.created.set(0);
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean("lazyInvalidTask", LazyInvalidTask.class,
                    definition -> definition.setLazyInit(true));
            context.refresh();

            assertThatThrownBy(() -> new KsetTaskLockAnnotationValidator(context)
                    .afterSingletonsInstantiated()).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("atMostFor");
            assertThat(LazyInvalidTask.created).hasValue(0);
        }
    }

    @Test
    void validatesImplementationMethodBehindJdkProxy() {
        ProxyFactory proxyFactory = new ProxyFactory(new ProxiedInvalidTask());
        proxyFactory.setInterfaces(TaskContract.class);
        proxyFactory.setProxyTargetClass(false);
        TaskContract proxy = (TaskContract) proxyFactory.getProxy();
        assertThat(Proxy.isProxyClass(proxy.getClass())).isTrue();
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean("proxiedInvalidTask", TaskContract.class, () -> proxy);
            context.refresh();

            assertThatThrownBy(() -> new KsetTaskLockAnnotationValidator(context)
                    .afterSingletonsInstantiated()).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("atMostFor")
                    .hasMessageContaining("ProxiedInvalidTask");
        }
    }

    @Component
    static class ValidTask {
        @KsetTaskLock(atMostFor = "10m", atLeastFor = "30s")
        public void run() {
        }
    }

    @Component
    static class InvalidTask {
        @KsetTaskLock(atMostFor = "十分钟")
        public void run() {
        }
    }

    static class LazyInvalidTask {
        static final AtomicInteger created = new AtomicInteger();

        LazyInvalidTask() {
            created.incrementAndGet();
        }

        @KsetTaskLock(atMostFor = "invalid-duration")
        public void run() {
        }
    }

    interface TaskContract {
        void run();
    }

    static class ProxiedInvalidTask implements TaskContract {
        @Override
        @KsetTaskLock(atMostFor = "invalid-duration")
        public void run() {
        }
    }
}
