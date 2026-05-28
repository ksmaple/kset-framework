package com.kset.monitor;

import com.kset.common.monitor.DubboAttachmentAccessor;
import com.kset.common.monitor.GatewayTraceBinding;
import com.kset.common.monitor.HttpTraceBinding;
import com.kset.common.monitor.MonitorScope;
import com.kset.common.monitor.TraceSnapshot;
import com.kset.common.monitor.internal.NoOpMonitorFacade;
import com.kset.monitor.facade.MetricKind;
import com.kset.monitor.facade.MonitorFacade;
import com.kset.monitor.facade.MonitorStatus;
import com.kset.monitor.facade.MonitorTransaction;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * 监控统一静态入口（CAT 风格门面）；推荐新代码使用本类，{@link com.kset.common.monitor.KsetMonitor} 为兼容别名。
 */
public final class Monitor {

    private static volatile MonitorFacade facade = new NoOpMonitorFacade();

    private Monitor() {
    }

    public static void install(MonitorFacade newFacade) {
        facade = Objects.requireNonNull(newFacade, "facade");
    }

    public static MonitorFacade facade() {
        return facade;
    }

    public static Optional<String> currentTraceId() {
        return facade.currentTraceId();
    }

    public static Optional<String> currentSpanId() {
        return facade.currentSpanId();
    }

    public static Optional<String> currentGrayTag() {
        return facade.currentGrayTag();
    }

    public static String generateTraceId() {
        return facade.generateTraceId();
    }

    public static String generateSpanId() {
        return facade.generateSpanId();
    }

    public static HttpTraceBinding bindHttpIncoming(String incomingTraceId) {
        return facade.bindHttpIncoming(incomingTraceId);
    }

    public static void bindHttpGrayTag(String incomingGrayTag, String defaultGray) {
        facade.bindHttpGrayTag(incomingGrayTag, defaultGray);
    }

    public static void clearHttpGrayTag() {
        facade.clearHttpGrayTag();
    }

    public static void bindDubboConsumer(DubboAttachmentAccessor attachments, String defaultGray) {
        facade.bindDubboConsumer(attachments, defaultGray);
    }

    public static void bindDubboProvider(DubboAttachmentAccessor attachments, String defaultGray) {
        facade.bindDubboProvider(attachments, defaultGray);
    }

    public static GatewayTraceBinding resolveGatewayTrace(String incomingTraceId, String traceHeaderName) {
        return facade.resolveGatewayTrace(incomingTraceId, traceHeaderName);
    }

    public static Object putReactorContext(Object context, String traceId, String grayTag) {
        return facade.putReactorContext(context, traceId, grayTag);
    }

    public static Optional<String> getFromReactor(Object contextView, String key) {
        return facade.getFromReactor(contextView, key);
    }

    public static void setTraceId(String traceId) {
        facade.setTraceId(traceId);
    }

    public static void setSpanId(String spanId) {
        facade.setSpanId(spanId);
    }

    public static void setGrayTag(String grayTag) {
        facade.setGrayTag(grayTag);
    }

    public static void clear() {
        facade.clear();
    }

    public static TraceSnapshot capture() {
        return facade.capture();
    }

    public static void restore(TraceSnapshot snapshot) {
        facade.restore(snapshot);
    }

    public static MonitorScope openScope(TraceSnapshot snapshot) {
        TraceSnapshot previous = capture();
        restore(snapshot);
        return new MonitorScope(previous);
    }

    public static MonitorTransaction newTransaction(String type, String name) {
        return facade.newTransaction(type, name);
    }

    public static void logEvent(String type, String name, MonitorStatus status, String data) {
        facade.logEvent(type, name, status, data);
    }

    public static void logMetric(String name, long value, MetricKind kind) {
        facade.logMetric(name, value, kind);
    }

    public static void logError(Throwable throwable, String message) {
        facade.logError(throwable, message);
    }

    @Deprecated
    public static void recordSlowEvent(String type, long costMs, String message) {
        facade.recordSlowEvent(type, costMs, message);
    }

    public static void runInTransaction(String type, String name, Runnable action) {
        try (MonitorTransaction tx = newTransaction(type, name)) {
            try {
                action.run();
                tx.setStatus(MonitorStatus.SUCCESS);
            } catch (RuntimeException e) {
                tx.setStatus(e);
                logError(e, e.getMessage() != null ? e.getMessage() : type + "." + name);
                throw e;
            }
        }
    }

    public static <T> T callInTransaction(String type, String name, Callable<T> action) throws Exception {
        try (MonitorTransaction tx = newTransaction(type, name)) {
            try {
                T result = action.call();
                tx.setStatus(MonitorStatus.SUCCESS);
                return result;
            } catch (Exception e) {
                tx.setStatus(e);
                logError(e, e.getMessage() != null ? e.getMessage() : type + "." + name);
                throw e;
            }
        }
    }
}
