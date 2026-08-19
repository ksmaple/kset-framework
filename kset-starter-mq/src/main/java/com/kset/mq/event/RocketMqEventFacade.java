package com.kset.mq.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kset.common.event.EventFacade;
import com.kset.common.event.SendCallback;
import com.kset.common.monitor.Monitor;
import com.kset.common.monitor.TraceSnapshot;
import com.kset.common.monitor.facade.MonitorTransaction;
import com.kset.common.trace.TraceHeaders;
import org.apache.rocketmq.client.apis.producer.SendReceipt;
import org.apache.rocketmq.client.autoconfigure.RocketMQProperties;
import org.apache.rocketmq.client.core.RocketMQClientTemplate;
import org.apache.rocketmq.client.support.RocketMQHeaders;
import org.springframework.core.env.Environment;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class RocketMqEventFacade implements EventFacade, RocketMqEventOperations {

    private final RocketMQClientTemplate rocketMqTemplate;
    private final ObjectMapper objectMapper;
    private final RocketMqEventDestinationResolver destinationResolver;

    public RocketMqEventFacade(RocketMQClientTemplate rocketMqTemplate,
                               ObjectMapper objectMapper,
                               RocketMQProperties rocketMqProperties,
                               Environment environment) {
        this.rocketMqTemplate = Objects.requireNonNull(rocketMqTemplate, "rocketMqTemplate must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.destinationResolver = new RocketMqEventDestinationResolver(
                Objects.requireNonNull(rocketMqProperties, "rocketMqProperties must not be null"),
                Objects.requireNonNull(environment, "environment must not be null"));
    }

    @Override
    public void publish(Object event) {
        Object payload = requireEvent(event);
        MonitorTransaction transaction = RocketMqEventMonitorSupport.beginPublish("sync", payload);
        try {
            SendReceipt receipt = rocketMqTemplate.syncSendNormalMessage(destinationResolver.destination(payload), message(payload));
            RocketMqEventMonitorSupport.addData(transaction, "messageId", receipt.getMessageId().toString());
            RocketMqEventMonitorSupport.success(transaction);
        } catch (RuntimeException | Error e) {
            RocketMqEventMonitorSupport.fail(transaction, e, "publish", RocketMqEventMonitorSupport.eventType(payload));
            throw e;
        } finally {
            RocketMqEventMonitorSupport.close(transaction);
        }
    }

    @Override
    public void publish(String topic, Object event) {
        publish(topic, null, event);
    }

    @Override
    public void publish(String topic, String tag, Object event) {
        Object payload = requireEvent(event);
        MonitorTransaction transaction = RocketMqEventMonitorSupport.beginPublish("sync", payload);
        try {
            SendReceipt receipt = rocketMqTemplate.syncSendNormalMessage(destinationResolver.destination(topic, tag),
                    message(payload));
            RocketMqEventMonitorSupport.addData(transaction, "messageId", receipt.getMessageId().toString());
            RocketMqEventMonitorSupport.success(transaction);
        } catch (RuntimeException | Error e) {
            RocketMqEventMonitorSupport.fail(transaction, e, "publish", RocketMqEventMonitorSupport.eventType(payload));
            throw e;
        } finally {
            RocketMqEventMonitorSupport.close(transaction);
        }
    }

    @Override
    public void publishAsync(Object event, SendCallback callback) {
        Object payload = requireEvent(event);
        CompletableFuture<SendReceipt> future = new CompletableFuture<>();
        try {
            rocketMqTemplate.asyncSendNormalMessage(destinationResolver.destination(payload), message(payload), future);
        } catch (RuntimeException | Error e) {
            if (callback != null) {
                callback.onException(e);
            }
            throw e;
        }
        future.whenComplete((receipt, throwable) -> handleAsyncResult(payload, callback, receipt, throwable));
    }

    @Override
    public void publishAsync(String topic, String tag, Object event, SendCallback callback) {
        Object payload = requireEvent(event);
        CompletableFuture<SendReceipt> future = new CompletableFuture<>();
        try {
            rocketMqTemplate.asyncSendNormalMessage(destinationResolver.destination(topic, tag), message(payload), future);
        } catch (RuntimeException | Error e) {
            if (callback != null) {
                callback.onException(e);
            }
            throw e;
        }
        future.whenComplete((receipt, throwable) -> handleAsyncResult(payload, callback, receipt, throwable));
    }

    @Override
    public void publishDelay(Object event, long delayMillis) {
        publishDelay(event, delayMillis, null);
    }

    @Override
    public void publishDelay(Object event, long delayMillis, SendCallback callback) {
        if (delayMillis < 0) {
            throw new IllegalArgumentException("delayMillis must not be negative");
        }
        Object payload = requireEvent(event);
        MonitorTransaction transaction = RocketMqEventMonitorSupport.beginPublish("delay", payload);
        try {
            SendReceipt receipt = rocketMqTemplate.syncSendDelayMessage(destinationResolver.destination(payload), message(payload),
                    Duration.ofMillis(delayMillis));
            RocketMqEventMonitorSupport.addData(transaction, "messageId", receipt.getMessageId().toString());
            RocketMqEventMonitorSupport.addData(transaction, "delayMillis", Long.toString(delayMillis));
            RocketMqEventMonitorSupport.success(transaction);
            if (callback != null) {
                callback.onSuccess();
            }
        } catch (RuntimeException | Error e) {
            RocketMqEventMonitorSupport.fail(transaction, e, "publishDelay", RocketMqEventMonitorSupport.eventType(payload));
            if (callback != null) {
                callback.onException(e);
            }
            throw e;
        } finally {
            RocketMqEventMonitorSupport.close(transaction);
        }
    }

    @Override
    public void publishDelay(String topic, String tag, Object event, long delayMillis) {
        if (delayMillis < 0) {
            throw new IllegalArgumentException("delayMillis must not be negative");
        }
        Object payload = requireEvent(event);
        MonitorTransaction transaction = RocketMqEventMonitorSupport.beginPublish("delay", payload);
        try {
            SendReceipt receipt = rocketMqTemplate.syncSendDelayMessage(destinationResolver.destination(topic, tag),
                    message(payload), Duration.ofMillis(delayMillis));
            RocketMqEventMonitorSupport.addData(transaction, "messageId", receipt.getMessageId().toString());
            RocketMqEventMonitorSupport.addData(transaction, "delayMillis", Long.toString(delayMillis));
            RocketMqEventMonitorSupport.success(transaction);
        } catch (RuntimeException | Error e) {
            RocketMqEventMonitorSupport.fail(transaction, e, "publishDelay", RocketMqEventMonitorSupport.eventType(payload));
            throw e;
        } finally {
            RocketMqEventMonitorSupport.close(transaction);
        }
    }

    @Override
    public void publishOrderly(Object event, String hashKey) {
        Object payload = requireEvent(event);
        MonitorTransaction transaction = RocketMqEventMonitorSupport.beginPublish("orderly", payload);
        RocketMqEventMonitorSupport.addData(transaction, "hashKey", hashKey);
        try {
            SendReceipt receipt = rocketMqTemplate.syncSendFifoMessage(destinationResolver.destination(payload), message(payload), hashKey);
            RocketMqEventMonitorSupport.addData(transaction, "messageId", receipt.getMessageId().toString());
            RocketMqEventMonitorSupport.success(transaction);
        } catch (RuntimeException | Error e) {
            RocketMqEventMonitorSupport.fail(transaction, e, "publishOrderly", RocketMqEventMonitorSupport.eventType(payload));
            throw e;
        } finally {
            RocketMqEventMonitorSupport.close(transaction);
        }
    }

    @Override
    public void publishOrderly(String topic, String tag, Object event, String hashKey) {
        Object payload = requireEvent(event);
        MonitorTransaction transaction = RocketMqEventMonitorSupport.beginPublish("orderly", payload);
        RocketMqEventMonitorSupport.addData(transaction, "hashKey", hashKey);
        try {
            SendReceipt receipt = rocketMqTemplate.syncSendFifoMessage(destinationResolver.destination(topic, tag),
                    message(payload), hashKey);
            RocketMqEventMonitorSupport.addData(transaction, "messageId", receipt.getMessageId().toString());
            RocketMqEventMonitorSupport.success(transaction);
        } catch (RuntimeException | Error e) {
            RocketMqEventMonitorSupport.fail(transaction, e, "publishOrderly", RocketMqEventMonitorSupport.eventType(payload));
            throw e;
        } finally {
            RocketMqEventMonitorSupport.close(transaction);
        }
    }

    @Override
    public void publishTransaction(Object event) {
        Object payload = requireEvent(event);
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish(payload);
                }
            });
            return;
        }
        publish(payload);
    }

    private Message<byte[]> message(Object payload) {
        try {
            String eventType = payload.getClass().getName();
            TraceSnapshot trace = Monitor.capture();
            MessageBuilder<byte[]> builder = MessageBuilder.withPayload(objectMapper.writeValueAsBytes(payload))
                    .setHeader(RocketMqEventHeaders.EVENT_TYPE, eventType)
                    .setHeader(RocketMqEventHeaders.EVENT_ID, UUID.randomUUID().toString())
                    .setHeader(RocketMQHeaders.KEYS, eventType);
            setHeaderIfPresent(builder, TraceHeaders.TRACE_ID_KEY,
                    trace.getTraceId() != null ? trace.getTraceId() : Monitor.generateTraceId());
            setHeaderIfPresent(builder, TraceHeaders.SPAN_ID_KEY, trace.getSpanId());
            setHeaderIfPresent(builder, TraceHeaders.GRAY_TAG_KEY, trace.getGrayTag());
            return builder.build();
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("event payload json serialization failed", e);
        }
    }

    private void setHeaderIfPresent(MessageBuilder<byte[]> builder, String key, String value) {
        if (value != null && !value.isBlank()) {
            builder.setHeader(key, value);
        }
    }

    private void handleAsyncResult(Object payload, SendCallback callback, SendReceipt receipt, Throwable throwable) {
        MonitorTransaction transaction = RocketMqEventMonitorSupport.beginPublish("async", payload);
        try {
            if (throwable == null) {
                RocketMqEventMonitorSupport.addData(transaction, "messageId", receipt.getMessageId().toString());
                RocketMqEventMonitorSupport.success(transaction);
                if (callback != null) {
                    callback.onSuccess();
                }
            } else {
                RocketMqEventMonitorSupport.fail(transaction, throwable, "publishAsync",
                        RocketMqEventMonitorSupport.eventType(payload));
                if (callback != null) {
                    callback.onException(throwable);
                }
            }
        } finally {
            RocketMqEventMonitorSupport.close(transaction);
        }
    }

    private Object requireEvent(Object event) {
        return Objects.requireNonNull(event, "event must not be null");
    }
}
