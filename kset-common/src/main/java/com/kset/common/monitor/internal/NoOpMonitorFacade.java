package com.kset.common.monitor.internal;

import com.kset.common.monitor.DubboAttachmentAccessor;
import com.kset.common.monitor.GatewayTraceBinding;
import com.kset.common.monitor.HttpTraceBinding;
import com.kset.common.monitor.facade.MonitorFacade;
import com.kset.common.monitor.TraceSnapshot;
import com.kset.common.monitor.facade.MetricKind;
import com.kset.common.monitor.facade.MonitorStatus;
import com.kset.common.monitor.facade.MonitorTransaction;

import java.util.Optional;
import java.util.UUID;

/**
 * 测试或显式关闭监控时使用的空实现；正常使用请依赖内置 {@link LogBackend} 或 starter 装配的门面。
 */
public final class NoOpMonitorFacade implements MonitorFacade {

    @Override
    public Optional<String> currentTraceId() {
        return Optional.empty();
    }

    @Override
    public Optional<String> currentSpanId() {
        return Optional.empty();
    }

    @Override
    public Optional<String> currentGrayTag() {
        return Optional.empty();
    }

    @Override
    public String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    @Override
    public String generateSpanId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    @Override
    public HttpTraceBinding bindHttpIncoming(String incomingTraceId) {
        String traceId = incomingTraceId != null && !incomingTraceId.isBlank()
                ? incomingTraceId : generateTraceId();
        return new HttpTraceBinding(traceId, generateSpanId());
    }

    @Override
    public void bindHttpGrayTag(String incomingGrayTag, String defaultGray) {
    }

    @Override
    public void clearHttpGrayTag() {
    }

    @Override
    public void bindDubboConsumer(DubboAttachmentAccessor attachments, String defaultGray) {
    }

    @Override
    public void bindDubboProvider(DubboAttachmentAccessor attachments, String defaultGray) {
    }

    @Override
    public GatewayTraceBinding resolveGatewayTrace(String incomingTraceId, String traceHeaderName) {
        String traceId = incomingTraceId != null && !incomingTraceId.isBlank()
                ? incomingTraceId : generateTraceId();
        return new GatewayTraceBinding(traceId, generateSpanId(), traceHeaderName);
    }

    @Override
    public Object putReactorContext(Object context, String traceId, String grayTag) {
        return context;
    }

    @Override
    public Optional<String> getFromReactor(Object contextView, String key) {
        return Optional.empty();
    }

    @Override
    public void setTraceId(String traceId) {
    }

    @Override
    public void setSpanId(String spanId) {
    }

    @Override
    public void setGrayTag(String grayTag) {
    }

    @Override
    public void clear() {
    }

    @Override
    public TraceSnapshot capture() {
        return new TraceSnapshot(null, null, null);
    }

    @Override
    public void restore(TraceSnapshot snapshot) {
    }

    @Override
    public MonitorTransaction newTransaction(String type, String name) {
        return new NoOpMonitorTransaction(type, name);
    }

    @Override
    public void logEvent(String type, String name, MonitorStatus status, String data) {
    }

    @Override
    public void logMetric(String name, long value, MetricKind kind) {
    }

    @Override
    public void logError(Throwable throwable, String message) {
    }
}
