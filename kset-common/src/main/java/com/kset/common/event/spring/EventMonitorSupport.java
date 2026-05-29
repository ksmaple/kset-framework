package com.kset.common.event.spring;

import com.kset.common.event.EventHandler;
import com.kset.common.monitor.Monitor;
import com.kset.common.monitor.facade.MonitorStatus;
import com.kset.common.monitor.facade.MonitorTransaction;
import com.kset.common.monitor.facade.MonitorTypes;
import com.kset.common.monitor.internal.NoOpMonitorTransaction;

final class EventMonitorSupport {

    private static final String CHANNEL = "spring";

    private EventMonitorSupport() {
    }

    static MonitorTransaction beginPublish(String mode, Object event) {
        return begin("event.publish." + mode, event, null);
    }

    static MonitorTransaction beginConsume(Object event, EventHandler<?> handler) {
        return begin("event.consume", event, handler);
    }

    static void success(MonitorTransaction transaction) {
        try {
            transaction.setStatus(MonitorStatus.SUCCESS);
        } catch (RuntimeException | Error ignored) {
            // Monitoring must not affect event flow.
        }
    }

    static void addData(MonitorTransaction transaction, String key, String value) {
        try {
            transaction.addData(key, value);
        } catch (RuntimeException | Error ignored) {
            // Monitoring must not affect event flow.
        }
    }

    static void close(MonitorTransaction transaction) {
        try {
            transaction.close();
        } catch (RuntimeException | Error ignored) {
            // Monitoring must not affect event flow.
        }
    }

    static void fail(MonitorTransaction transaction, Throwable throwable, String action, Object event) {
        try {
            transaction.setStatus(throwable);
            transaction.addData("action", action);
            Monitor.logError(throwable, action + " event failed: " + eventType(event));
        } catch (RuntimeException | Error ignored) {
            // Monitoring must not override the original event failure.
        }
    }

    static void logScheduled(Object event, long delayMillis) {
        try {
            Monitor.logEvent(MonitorTypes.MQ,
                    "event.publish.delay.scheduled",
                    MonitorStatus.SUCCESS,
                    "channel=" + CHANNEL + ",eventType=" + eventType(event) + ",delayMillis=" + delayMillis);
        } catch (RuntimeException | Error ignored) {
            // Monitoring must not affect event flow.
        }
    }

    static String eventType(Object event) {
        return event == null ? "null" : event.getClass().getName();
    }

    private static MonitorTransaction begin(String name, Object event, EventHandler<?> handler) {
        try {
            MonitorTransaction transaction = Monitor.newTransaction(MonitorTypes.MQ, name);
            transaction.addData("channel", CHANNEL);
            transaction.addData("eventType", eventType(event));
            if (handler != null) {
                transaction.addData("handler", handler.getClass().getName());
            }
            return transaction;
        } catch (RuntimeException | Error ignored) {
            return new NoOpMonitorTransaction(MonitorTypes.MQ, name);
        }
    }
}
