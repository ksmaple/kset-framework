package com.kset.monitor.facade;

import com.kset.common.monitor.DubboAttachmentAccessor;
import com.kset.common.monitor.GatewayTraceBinding;
import com.kset.common.monitor.HttpTraceBinding;
import com.kset.common.monitor.TraceSnapshot;

import java.util.Optional;

/**
 * 统一监控门面：链路上下文传播 + CAT 风格 Transaction/Event/Metric。
 */
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

    void logEvent(String type, String name, MonitorStatus status, String data);

    void logMetric(String name, long value, MetricKind kind);

    void logError(Throwable throwable, String message);

    /**
     * @deprecated 请使用 {@link #logEvent(String, String, MonitorStatus, String)} 或 {@link #newTransaction(String, String)}
     */
    @Deprecated
    default void recordSlowEvent(String type, long costMs, String message) {
        logEvent(type, "slow", MonitorStatus.FAIL, costMs + "ms " + message);
    }
}
