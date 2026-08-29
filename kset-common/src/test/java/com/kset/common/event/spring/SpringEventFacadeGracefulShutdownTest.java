package com.kset.common.event.spring;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class SpringEventFacadeGracefulShutdownTest {

    @Test
    void stopDrainsAsyncTasks() {
        CountDownLatch taskRan = new CountDownLatch(1);
        ApplicationEventPublisher publisher = event -> taskRan.countDown();
        SpringEventFacade facade = new SpringEventFacade(publisher, null);
        facade.setShutdownTimeout(Duration.ofSeconds(5));

        facade.start();
        facade.publishAsync("event", null);
        facade.stop();

        assertThat(taskRan.getCount()).as("停机前排空异步任务").isZero();
        assertThat(facade.isRunning()).isFalse();
    }

    @Test
    void stopDrainsDelayedTasks() throws Exception {
        CountDownLatch taskRan = new CountDownLatch(1);
        ApplicationEventPublisher publisher = event -> taskRan.countDown();
        SpringEventFacade facade = new SpringEventFacade(publisher, null);
        facade.setShutdownTimeout(Duration.ofSeconds(5));

        facade.start();
        facade.publishDelay("event", 100, null);
        facade.stop();

        assertThat(taskRan.await(1, TimeUnit.SECONDS)).as("延迟任务应在排空中投递").isTrue();
    }

    @Test
    void destroyWithoutStartStillReleasesExecutors() {
        SpringEventFacade facade = new SpringEventFacade(event -> { }, null);

        facade.destroy();
        facade.destroy();

        assertThat(facade.isRunning()).isFalse();
    }
}
