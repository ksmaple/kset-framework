package com.kset.common.event.spring;

import com.kset.common.event.SendCallback;
import com.kset.common.context.KsetContext;
import com.kset.common.context.KsetContextKeys;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.time.Duration;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpringEventFacadeDelayTest {

    @Test
    void delayFailureNotifiesCallback() throws Exception {
        ApplicationEventPublisher publisher = event -> {
            throw new IllegalStateException("boom");
        };
        SpringEventFacade facade = new SpringEventFacade(publisher, null);
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        try {
            facade.publishDelay("payload", 20, new SendCallback() {
                @Override
                public void onSuccess() {
                    done.countDown();
                }

                @Override
                public void onException(Throwable throwable) {
                    error.set(throwable);
                    done.countDown();
                }
            });

            assertThat(done.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(error.get()).isInstanceOf(IllegalStateException.class).hasMessage("boom");
        } finally {
            facade.destroy();
        }
    }

    @Test
    void delaySuccessNotifiesCallback() throws Exception {
        SpringEventFacade facade = new SpringEventFacade(event -> {
        }, null);
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        try {
            facade.publishDelay("payload", 20, new SendCallback() {
                @Override
                public void onSuccess() {
                    done.countDown();
                }

                @Override
                public void onException(Throwable throwable) {
                    error.set(throwable);
                    done.countDown();
                }
            });

            assertThat(done.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(error.get()).isNull();
        } finally {
            facade.destroy();
        }
    }

    @Test
    void delayPublishesWithCapturedContext() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<String> seen = new AtomicReference<>();
        SpringEventFacade facade = new SpringEventFacade(event ->
                seen.set(KsetContext.get(KsetContextKeys.TRACE_ID).orElse("missing")), null);
        KsetContext.put(KsetContextKeys.TRACE_ID, "trace-delay");
        try {
            facade.publishDelay("payload", 20, new SendCallback() {
                @Override
                public void onSuccess() {
                    done.countDown();
                }

                @Override
                public void onException(Throwable throwable) {
                    done.countDown();
                }
            });
            assertThat(done.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(seen.get()).isEqualTo("trace-delay");
        } finally {
            KsetContext.clear();
            facade.destroy();
        }
    }

    @Test
    void rejectedDelayNotifiesFailure() {
        SpringEventFacade facade = new SpringEventFacade(event -> { }, null);
        facade.destroy();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        assertThatThrownBy(() -> facade.publishDelay("payload", 10, new SendCallback() {
            @Override
            public void onSuccess() {
            }

            @Override
            public void onException(Throwable throwable) {
                failure.set(throwable);
            }
        })).isInstanceOf(RejectedExecutionException.class);
        assertThat(failure.get()).isInstanceOf(RejectedExecutionException.class);
    }

    @Test
    void forcedShutdownNotifiesPendingDelay() {
        SpringEventFacade facade = new SpringEventFacade(event -> { }, null);
        facade.setShutdownTimeout(Duration.ofMillis(10));
        AtomicReference<Throwable> failure = new AtomicReference<>();
        facade.start();
        facade.publishDelay("payload", 60_000, new SendCallback() {
            @Override
            public void onSuccess() {
            }

            @Override
            public void onException(Throwable throwable) {
                failure.set(throwable);
            }
        });

        facade.stop();

        assertThat(failure.get()).isInstanceOf(RejectedExecutionException.class);
    }
}
