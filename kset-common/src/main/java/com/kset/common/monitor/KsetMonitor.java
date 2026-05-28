package com.kset.common.monitor;

import com.kset.monitor.Monitor;
import com.kset.monitor.facade.MetricKind;
import com.kset.monitor.facade.MonitorStatus;
import com.kset.monitor.facade.MonitorTransaction;

import java.util.Optional;

/**
 * 全链路监控统一静态入口（兼容层，委托 {@link Monitor}）。
 *
 * @deprecated 请使用 {@link Monitor}，本类保留兼容。
 */
@Deprecated
public final class KsetMonitor {

    private KsetMonitor() {
    }

    public static void install(KsetMonitorFacade newFacade) {
        Monitor.install(newFacade);
    }

    public static KsetMonitorFacade facade() {
        return (KsetMonitorFacade) Monitor.facade();
    }

    public static Optional<String> currentTraceId() {
        return Monitor.currentTraceId();
    }

    public static Optional<String> currentSpanId() {
        return Monitor.currentSpanId();
    }

    public static Optional<String> currentGrayTag() {
        return Monitor.currentGrayTag();
    }

    public static String generateTraceId() {
        return Monitor.generateTraceId();
    }

    public static String generateSpanId() {
        return Monitor.generateSpanId();
    }

    public static HttpTraceBinding bindHttpIncoming(String incomingTraceId) {
        return Monitor.bindHttpIncoming(incomingTraceId);
    }

    public static void bindHttpGrayTag(String incomingGrayTag, String defaultGray) {
        Monitor.bindHttpGrayTag(incomingGrayTag, defaultGray);
    }

    public static void clearHttpGrayTag() {
        Monitor.clearHttpGrayTag();
    }

    public static void bindDubboConsumer(DubboAttachmentAccessor attachments, String defaultGray) {
        Monitor.bindDubboConsumer(attachments, defaultGray);
    }

    public static void bindDubboProvider(DubboAttachmentAccessor attachments, String defaultGray) {
        Monitor.bindDubboProvider(attachments, defaultGray);
    }

    public static GatewayTraceBinding resolveGatewayTrace(String incomingTraceId, String traceHeaderName) {
        return Monitor.resolveGatewayTrace(incomingTraceId, traceHeaderName);
    }

    public static Object putReactorContext(Object context, String traceId, String grayTag) {
        return Monitor.putReactorContext(context, traceId, grayTag);
    }

    public static Optional<String> getFromReactor(Object contextView, String key) {
        return Monitor.getFromReactor(contextView, key);
    }

    public static void setTraceId(String traceId) {
        Monitor.setTraceId(traceId);
    }

    public static void setSpanId(String spanId) {
        Monitor.setSpanId(spanId);
    }

    public static void setGrayTag(String grayTag) {
        Monitor.setGrayTag(grayTag);
    }

    public static void clear() {
        Monitor.clear();
    }

    public static TraceSnapshot capture() {
        return Monitor.capture();
    }

    public static void restore(TraceSnapshot snapshot) {
        Monitor.restore(snapshot);
    }

    public static MonitorScope openScope(TraceSnapshot snapshot) {
        return Monitor.openScope(snapshot);
    }

    public static MonitorTransaction newTransaction(String type, String name) {
        return Monitor.newTransaction(type, name);
    }

    public static void logEvent(String type, String name, MonitorStatus status, String data) {
        Monitor.logEvent(type, name, status, data);
    }

    public static void logMetric(String name, long value, MetricKind kind) {
        Monitor.logMetric(name, value, kind);
    }

    public static void logError(Throwable throwable, String message) {
        Monitor.logError(throwable, message);
    }

    @Deprecated
    public static void recordSlowEvent(String type, long costMs, String message) {
        Monitor.recordSlowEvent(type, costMs, message);
    }
}
