package com.kset.common.event.spring;

import com.kset.common.context.KsetContext;
import com.kset.common.context.KsetContextScope;
import com.kset.common.context.KsetContextSnapshot;
import com.kset.common.event.EventFacade;
import com.kset.common.event.SendCallback;
import com.kset.common.monitor.Monitor;
import com.kset.common.monitor.MonitorScope;
import com.kset.common.monitor.TraceSnapshot;
import com.kset.common.monitor.facade.MonitorTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.task.TaskExecutor;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 进程内 Spring 事件实现。同 {@code hashKey} 按 64 槽串行；delay/async 会恢复调用线程的上下文。
 */
public class SpringEventFacade implements EventFacade, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(SpringEventFacade.class);
    private static final int ORDERLY_STRIPES = 64;

    private final ApplicationEventPublisher publisher;
    private final TaskExecutor asyncExecutor;
    private final ExecutorService ownedAsyncExecutor;
    private final ScheduledExecutorService delayExecutor;
    private final Object[] orderlyStripes;

    public SpringEventFacade(ApplicationEventPublisher publisher, TaskExecutor asyncExecutor) {
        this.publisher = Objects.requireNonNull(publisher, "publisher must not be null");
        if (asyncExecutor != null) {
            this.asyncExecutor = asyncExecutor;
            this.ownedAsyncExecutor = null;
        } else {
            AtomicInteger threadId = new AtomicInteger();
            this.ownedAsyncExecutor = new ThreadPoolExecutor(
                    2, 8, 60L, TimeUnit.SECONDS,
                    new ArrayBlockingQueue<>(1000),
                    runnable -> {
                        Thread thread = new Thread(runnable,
                                "kset-event-async-" + threadId.incrementAndGet());
                        thread.setDaemon(true);
                        return thread;
                    },
                    new ThreadPoolExecutor.CallerRunsPolicy());
            this.asyncExecutor = ownedAsyncExecutor::execute;
        }
        this.delayExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "kset-event-delay");
            thread.setDaemon(true);
            return thread;
        });
        this.orderlyStripes = new Object[ORDERLY_STRIPES];
        for (int i = 0; i < ORDERLY_STRIPES; i++) {
            orderlyStripes[i] = new Object();
        }
    }

    @Override
    public void publish(Object event) {
        Object payload = requireEvent(event);
        MonitorTransaction transaction = EventMonitorSupport.beginPublish("sync", payload);
        try {
            publisher.publishEvent(new SpringEventWrapper(payload));
            EventMonitorSupport.success(transaction);
        } catch (RuntimeException | Error e) {
            EventMonitorSupport.fail(transaction, e, "publish", payload);
            throw e;
        } finally {
            EventMonitorSupport.close(transaction);
        }
    }

    @Override
    public void publishAsync(Object event, SendCallback callback) {
        Object payload = requireEvent(event);
        try {
            asyncExecutor.execute(withCapturedContext(() -> publishAsyncNow(payload, callback)));
        } catch (RejectedExecutionException e) {
            MonitorTransaction transaction = EventMonitorSupport.beginPublish("async.submit", payload);
            EventMonitorSupport.fail(transaction, e, "publishAsyncSubmit", payload);
            EventMonitorSupport.close(transaction);
            if (callback != null) {
                callback.onException(e);
            }
            throw e;
        }
    }

    @Override
    public void publishDelay(Object event, long delayMillis, SendCallback callback) {
        if (delayMillis < 0) {
            throw new IllegalArgumentException("delayMillis must not be negative");
        }
        Object payload = requireEvent(event);
        EventMonitorSupport.logScheduled(payload, delayMillis);
        delayExecutor.schedule(withCapturedContext(() -> publishDelayNow(payload, callback)), delayMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * 保留原因：延迟/异步线程不恢复 KsetContext 与 Trace，监听器读不到登录用户和 trace。
     */
    @SuppressWarnings("unused")
    private Runnable withCapturedContextForRollback(Runnable task) {
        return task;
    }

    private Runnable withCapturedContext(Runnable task) {
        KsetContextSnapshot context = KsetContext.capture();
        TraceSnapshot trace = Monitor.capture();
        return () -> {
            try (KsetContextScope ignoredCtx = KsetContext.openScope(context);
                 MonitorScope ignoredTrace = Monitor.openScope(trace)) {
                task.run();
            }
        };
    }

    private void publishAsyncNow(Object payload, SendCallback callback) {
        MonitorTransaction transaction = EventMonitorSupport.beginPublish("async", payload);
        try {
            publisher.publishEvent(new SpringEventWrapper(payload));
            EventMonitorSupport.success(transaction);
            if (callback != null) {
                callback.onSuccess();
            }
        } catch (Throwable throwable) {
            EventMonitorSupport.fail(transaction, throwable, "publishAsync", payload);
            if (callback != null) {
                callback.onException(throwable);
            }
            if (throwable instanceof Error error) {
                throw error;
            }
        } finally {
            EventMonitorSupport.close(transaction);
        }
    }

    private void publishDelayNow(Object payload, SendCallback callback) {
        MonitorTransaction transaction = EventMonitorSupport.beginPublish("delay", payload);
        try {
            publisher.publishEvent(new SpringEventWrapper(payload));
            EventMonitorSupport.success(transaction);
            if (callback != null) {
                callback.onSuccess();
            }
        } catch (Throwable throwable) {
            EventMonitorSupport.fail(transaction, throwable, "publishDelay", payload);
            log.error("publishDelay failed eventType={}", EventMonitorSupport.eventType(payload), throwable);
            if (callback != null) {
                callback.onException(throwable);
            }
        } finally {
            EventMonitorSupport.close(transaction);
        }
    }

    /**
     * 保留原因：延迟发布失败只记监控，调用方无感知。
     */
    @SuppressWarnings("unused")
    private void publishDelayForRollback(Object event, long delayMillis) {
        if (delayMillis < 0) {
            throw new IllegalArgumentException("delayMillis must not be negative");
        }
        Object payload = requireEvent(event);
        EventMonitorSupport.logScheduled(payload, delayMillis);
        delayExecutor.schedule(() -> {
            MonitorTransaction transaction = EventMonitorSupport.beginPublish("delay", payload);
            try {
                publisher.publishEvent(new SpringEventWrapper(payload));
                EventMonitorSupport.success(transaction);
            } catch (Throwable throwable) {
                EventMonitorSupport.fail(transaction, throwable, "publishDelay", payload);
            } finally {
                EventMonitorSupport.close(transaction);
            }
        }, delayMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public void publishOrderly(Object event, String hashKey) {
        publishOrderlySerialized(event, hashKey);
    }

    private void publishOrderlySerialized(Object event, String hashKey) {
        synchronized (orderlyStripe(hashKey)) {
            doPublishOrderly(event, hashKey);
        }
    }

    private void doPublishOrderly(Object event, String hashKey) {
        Object payload = requireEvent(event);
        MonitorTransaction transaction = EventMonitorSupport.beginPublish("orderly", payload);
        EventMonitorSupport.addData(transaction, "hashKey", hashKey);
        try {
            publisher.publishEvent(new OrderlyEventWrapper(payload, hashKey));
            EventMonitorSupport.success(transaction);
        } catch (RuntimeException | Error e) {
            EventMonitorSupport.fail(transaction, e, "publishOrderly", payload);
            throw e;
        } finally {
            EventMonitorSupport.close(transaction);
        }
    }

    /**
     * 保留原因：同 hashKey 并发发布不串行，无法保证本地顺序。
     */
    @SuppressWarnings("unused")
    private void publishOrderlyForRollback(Object event, String hashKey) {
        Object payload = requireEvent(event);
        MonitorTransaction transaction = EventMonitorSupport.beginPublish("orderly", payload);
        EventMonitorSupport.addData(transaction, "hashKey", hashKey);
        try {
            publisher.publishEvent(new OrderlyEventWrapper(payload, hashKey));
            EventMonitorSupport.success(transaction);
        } catch (RuntimeException | Error e) {
            EventMonitorSupport.fail(transaction, e, "publishOrderly", payload);
            throw e;
        } finally {
            EventMonitorSupport.close(transaction);
        }
    }

    private Object orderlyStripe(String hashKey) {
        int hash = hashKey == null ? 0 : hashKey.hashCode();
        return orderlyStripes[hash & (ORDERLY_STRIPES - 1)];
    }

    @Override
    public void publishTransaction(Object event) {
        Object payload = requireEvent(event);
        boolean transactionActive = TransactionSynchronizationManager.isActualTransactionActive();
        String mode = transactionActive ? "transaction" : "transaction.immediate";
        MonitorTransaction transaction = EventMonitorSupport.beginPublish(mode, payload);
        try {
            if (transactionActive) {
                publisher.publishEvent(new TransactionalEventWrapper(payload));
            } else {
                publisher.publishEvent(new SpringEventWrapper(payload));
            }
            EventMonitorSupport.success(transaction);
        } catch (RuntimeException | Error e) {
            EventMonitorSupport.fail(transaction, e, "publishTransaction", payload);
            throw e;
        } finally {
            EventMonitorSupport.close(transaction);
        }
    }

    @Override
    public void destroy() {
        delayExecutor.shutdownNow();
        if (ownedAsyncExecutor != null) {
            ownedAsyncExecutor.shutdownNow();
        }
    }

    private Object requireEvent(Object event) {
        return Objects.requireNonNull(event, "event must not be null");
    }
}
