package com.kset.common.event.spring;

import com.kset.common.event.EventFacade;
import com.kset.common.event.SendCallback;
import com.kset.common.monitor.facade.MonitorTransaction;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SpringEventFacade implements EventFacade, DisposableBean {

    private final ApplicationEventPublisher publisher;
    private final TaskExecutor asyncExecutor;
    private final ScheduledExecutorService delayExecutor;

    public SpringEventFacade(ApplicationEventPublisher publisher, TaskExecutor asyncExecutor) {
        this.publisher = Objects.requireNonNull(publisher, "publisher must not be null");
        this.asyncExecutor = asyncExecutor != null ? asyncExecutor : new SimpleAsyncTaskExecutor("kset-event-async-");
        this.delayExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "kset-event-delay");
            thread.setDaemon(true);
            return thread;
        });
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
            asyncExecutor.execute(() -> {
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
            });
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
    public void publishDelay(Object event, long delayMillis) {
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
    }

    private Object requireEvent(Object event) {
        return Objects.requireNonNull(event, "event must not be null");
    }
}
