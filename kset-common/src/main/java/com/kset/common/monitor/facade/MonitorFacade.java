package com.kset.common.monitor.facade;

import com.kset.common.monitor.DubboAttachmentAccessor;
import com.kset.common.monitor.GatewayTraceBinding;
import com.kset.common.monitor.HttpTraceBinding;
import com.kset.common.monitor.TraceSnapshot;

import java.util.Optional;


public interface MonitorFacade {

    Optional<String> currentTraceId();

    Optional<String> currentSpanId();

    Optional<String> currentGrayTag();

    String generateTraceId();

    String generateSpanId();

    HttpTraceBinding bindHttpIncoming(String incomingTraceId);

    void bindHttpGrayTag(String incomingGrayTag, String defaultGray);

    void clearHttpGrayTag();

    void bindDubboConsumer(DubboAttachmentAccessor attachments, String defaultGray);

    void bindDubboProvider(DubboAttachmentAccessor attachments, String defaultGray);

    GatewayTraceBinding resolveGatewayTrace(String incomingTraceId, String traceHeaderName);

    Object putReactorContext(Object context, String traceId, String grayTag);

    Optional<String> getFromReactor(Object contextView, String key);

    void setTraceId(String traceId);

    void setSpanId(String spanId);

    void setGrayTag(String grayTag);

    void clear();

    TraceSnapshot capture();

    void restore(TraceSnapshot snapshot);

    MonitorTransaction newTransaction(String type, String name);

    default MonitorTransaction newTransaction(String type, String name, long startedNanos) {
        return newTransaction(type, name);
    }

    void logEvent(String type, String name, MonitorStatus status, String data);

    void logMetric(String name, long value, MetricKind kind);

    void logError(Throwable throwable, String message);
}
