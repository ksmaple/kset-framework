package com.kset.monitor.internal;

import com.kset.common.monitor.DubboAttachmentAccessor;
import com.kset.common.monitor.GatewayTraceBinding;
import com.kset.common.monitor.HttpTraceBinding;
import com.kset.common.monitor.KsetMonitorFacade;
import com.kset.common.monitor.TraceSnapshot;
import com.kset.monitor.facade.MetricKind;
import com.kset.monitor.facade.MonitorStatus;
import com.kset.monitor.facade.MonitorTransaction;
import com.kset.common.trace.TraceHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

import java.util.Optional;
import java.util.UUID;

/**
 * 基于 SLF4J MDC（及 Reactor Context）的默认监控门面实现。
 *
 * @deprecated 请使用 {@link com.kset.monitor.internal.DefaultMonitorFacade}。
 */
@Deprecated
public final class MdcMonitorFacade implements KsetMonitorFacade {

    private static final Logger log = LoggerFactory.getLogger(MdcMonitorFacade.class);

    @Override
    public Optional<String> currentTraceId() {
        return Optional.ofNullable(MDC.get(TraceHeaders.TRACE_ID_KEY));
    }

    @Override
    public Optional<String> currentSpanId() {
        return Optional.ofNullable(MDC.get(TraceHeaders.SPAN_ID_KEY));
    }

    @Override
    public Optional<String> currentGrayTag() {
        return Optional.ofNullable(MDC.get(TraceHeaders.GRAY_TAG_KEY));
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
        String traceId = firstNonBlank(incomingTraceId, generateTraceId());
        String spanId = generateSpanId();
        setTraceId(traceId);
        setSpanId(spanId);
        return new HttpTraceBinding(traceId, spanId);
    }

    @Override
    public void bindHttpGrayTag(String incomingGrayTag, String defaultGray) {
        String grayTag = firstNonBlank(incomingGrayTag, defaultGray);
        setGrayTag(grayTag);
    }

    @Override
    public void clearHttpGrayTag() {
        MDC.remove(TraceHeaders.GRAY_TAG_KEY);
    }

    @Override
    public void bindDubboConsumer(DubboAttachmentAccessor attachments, String defaultGray) {
        String traceId = currentTraceId().orElseGet(this::generateTraceId);
        String grayTag = currentGrayTag().orElse(defaultGray);
        attachments.setAttachment(TraceHeaders.TRACE_ID_KEY, traceId);
        attachments.setAttachment(TraceHeaders.GRAY_TAG_KEY, grayTag);
        setTraceId(traceId);
        setGrayTag(grayTag);
    }

    @Override
    public void bindDubboProvider(DubboAttachmentAccessor attachments, String defaultGray) {
        String traceId = attachments.getAttachment(TraceHeaders.TRACE_ID_KEY);
        String grayTag = attachments.getAttachment(TraceHeaders.GRAY_TAG_KEY);

        if (traceId == null) {
            traceId = generateTraceId();
        }
        if (grayTag == null) {
            grayTag = defaultGray;
        }
        setTraceId(traceId);
        setGrayTag(grayTag);
    }

    @Override
    public GatewayTraceBinding resolveGatewayTrace(String incomingTraceId, String traceHeaderName) {
        String traceId = firstNonBlank(incomingTraceId, generateTraceId());
        String spanId = generateSpanId();
        return new GatewayTraceBinding(traceId, spanId, traceHeaderName);
    }

    @Override
    public Object putReactorContext(Object context, String traceId, String grayTag) {
        Context reactorContext = (Context) context;
        Context updated = reactorContext;
        if (traceId != null) {
            updated = updated.put(TraceHeaders.TRACE_ID_KEY, traceId);
        }
        if (grayTag != null) {
            updated = updated.put(TraceHeaders.GRAY_TAG_KEY, grayTag);
        }
        return updated;
    }

    @Override
    public Optional<String> getFromReactor(Object contextView, String key) {
        ContextView view = (ContextView) contextView;
        return view.hasKey(key) ? Optional.of(view.get(key)) : Optional.empty();
    }

    @Override
    public void setTraceId(String traceId) {
        if (traceId != null) {
            MDC.put(TraceHeaders.TRACE_ID_KEY, traceId);
        }
    }

    @Override
    public void setSpanId(String spanId) {
        if (spanId != null) {
            MDC.put(TraceHeaders.SPAN_ID_KEY, spanId);
        }
    }

    @Override
    public void setGrayTag(String grayTag) {
        if (grayTag != null) {
            MDC.put(TraceHeaders.GRAY_TAG_KEY, grayTag);
        }
    }

    @Override
    public void clear() {
        MDC.remove(TraceHeaders.TRACE_ID_KEY);
        MDC.remove(TraceHeaders.SPAN_ID_KEY);
        MDC.remove(TraceHeaders.GRAY_TAG_KEY);
    }

    @Override
    public TraceSnapshot capture() {
        return new TraceSnapshot(
                MDC.get(TraceHeaders.TRACE_ID_KEY),
                MDC.get(TraceHeaders.SPAN_ID_KEY),
                MDC.get(TraceHeaders.GRAY_TAG_KEY));
    }

    @Override
    public void restore(TraceSnapshot snapshot) {
        if (snapshot == null) {
            clear();
            return;
        }
        clear();
        if (snapshot.getTraceId() != null) {
            setTraceId(snapshot.getTraceId());
        }
        if (snapshot.getSpanId() != null) {
            setSpanId(snapshot.getSpanId());
        }
        if (snapshot.getGrayTag() != null) {
            setGrayTag(snapshot.getGrayTag());
        }
    }

    @Override
    public MonitorTransaction newTransaction(String type, String name) {
        return new NoOpMonitorTransaction(type, name);
    }

    @Override
    public void logEvent(String type, String name, MonitorStatus status, String data) {
        if (status == MonitorStatus.FAIL) {
            log.warn("monitor-event type={} name={} data={}", type, name, data);
        }
    }

    @Override
    public void logMetric(String name, long value, MetricKind kind) {
    }

    @Override
    public void logError(Throwable throwable, String message) {
        log.error("monitor-error message={}", message, throwable);
    }

    @Override
    public void recordSlowEvent(String type, long costMs, String message) {
        log.warn("slow-event type={} costMs={} traceId={} message={}",
                type, costMs, currentTraceId().orElse("-"), message);
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }
}
