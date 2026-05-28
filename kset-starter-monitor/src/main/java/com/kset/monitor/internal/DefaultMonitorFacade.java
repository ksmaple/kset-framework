package com.kset.monitor.internal;

import com.kset.common.monitor.DubboAttachmentAccessor;
import com.kset.common.monitor.GatewayTraceBinding;
import com.kset.common.monitor.HttpTraceBinding;
import com.kset.common.monitor.KsetMonitorFacade;
import com.kset.common.monitor.TraceSnapshot;
import com.kset.common.trace.TraceHeaders;
import com.kset.monitor.backend.MonitorBackend;
import com.kset.monitor.facade.CompletedTransaction;
import com.kset.monitor.facade.MetricKind;
import com.kset.monitor.facade.MonitorEvent;
import com.kset.monitor.facade.MonitorMetric;
import com.kset.monitor.facade.MonitorStatus;
import com.kset.monitor.facade.MonitorTransaction;
import com.kset.monitor.facade.MonitorTypes;
import com.kset.monitor.reporter.AsyncReporter;
import com.kset.monitor.reporter.MetricAggregator;
import com.kset.monitor.sampler.Sampler;
import org.slf4j.MDC;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 默认监控门面：MDC 链路上下文 + Transaction 栈 + 可插拔 {@link MonitorBackend}。
 */
public final class DefaultMonitorFacade implements KsetMonitorFacade {

    private final MonitorBackend backend;
    private final Sampler sampler;
    private final AsyncReporter asyncReporter;
    private final MetricAggregator metricAggregator;
    private final boolean asyncEnabled;

    private final ThreadLocal<Deque<ActiveTransaction>> transactionStack =
            ThreadLocal.withInitial(ArrayDeque::new);

    public DefaultMonitorFacade(MonitorBackend backend,
                                Sampler sampler,
                                AsyncReporter asyncReporter,
                                MetricAggregator metricAggregator,
                                boolean asyncEnabled) {
        this.backend = backend;
        this.sampler = sampler;
        this.asyncReporter = asyncReporter;
        this.metricAggregator = metricAggregator;
        this.asyncEnabled = asyncEnabled;
    }

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
        setGrayTag(firstNonBlank(incomingGrayTag, defaultGray));
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
        return new GatewayTraceBinding(traceId, generateSpanId(), traceHeaderName);
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
        transactionStack.remove();
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
        MDC.remove(TraceHeaders.TRACE_ID_KEY);
        MDC.remove(TraceHeaders.SPAN_ID_KEY);
        MDC.remove(TraceHeaders.GRAY_TAG_KEY);
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
        String traceId = currentTraceId().orElse(null);
        if (!sampler.shouldSample(traceId, type)) {
            return new com.kset.monitor.internal.NoOpMonitorTransaction(type, name);
        }
        ActiveTransaction active = new ActiveTransaction(type, name, System.nanoTime());
        transactionStack.get().push(active);
        return new FacadeMonitorTransaction(active);
    }

    @Override
    public void logEvent(String type, String name, MonitorStatus status, String data) {
        MonitorEvent event = new MonitorEvent(type, name, status, data, currentTraceId());
        dispatch(() -> backend.logEvent(event));
    }

    @Override
    public void logMetric(String name, long value, MetricKind kind) {
        metricAggregator.record(name, value, kind);
        MonitorMetric metric = new MonitorMetric(name, value, kind, currentTraceId());
        dispatch(() -> backend.logMetric(metric));
    }

    @Override
    public void logError(Throwable throwable, String message) {
        String traceId = currentTraceId().orElse("-");
        dispatch(() -> {
            if (backend instanceof com.kset.monitor.backend.LogBackend logBackend) {
                logBackend.logError(traceId, throwable, message);
            } else {
                backend.logError(throwable, message);
            }
            backend.logEvent(new MonitorEvent(MonitorTypes.ERROR,
                    throwable != null ? throwable.getClass().getSimpleName() : "Error",
                    MonitorStatus.FAIL,
                    message,
                    Optional.of(traceId)));
        });
    }

    @Override
    public void recordSlowEvent(String type, long costMs, String message) {
        logEvent(type, "slow", MonitorStatus.FAIL, costMs + "ms " + message);
    }

    private void completeTransaction(ActiveTransaction active) {
        Deque<ActiveTransaction> stack = transactionStack.get();
        if (stack.isEmpty() || stack.peek() != active) {
            return;
        }
        stack.pop();
        long durationMs = (System.nanoTime() - active.startNanos) / 1_000_000L;
        String parentType = stack.isEmpty() ? null : stack.peek().type;
        CompletedTransaction completed = new CompletedTransaction(
                active.type,
                active.name,
                durationMs,
                active.status,
                Map.copyOf(active.data),
                currentTraceId(),
                Optional.ofNullable(parentType));
        dispatch(() -> backend.completeTransaction(completed));
    }

    private void dispatch(Runnable task) {
        if (!backend.isEnabled()) {
            return;
        }
        if (asyncEnabled && asyncReporter != null) {
            asyncReporter.report(task);
        } else {
            task.run();
        }
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

    private static final class ActiveTransaction {
        private final String type;
        private final String name;
        private final long startNanos;
        private MonitorStatus status = MonitorStatus.SUCCESS;
        private final Map<String, String> data = new HashMap<>();

        private ActiveTransaction(String type, String name, long startNanos) {
            this.type = type;
            this.name = name;
            this.startNanos = startNanos;
        }
    }

    private final class FacadeMonitorTransaction implements MonitorTransaction {

        private final ActiveTransaction active;
        private boolean completed;

        private FacadeMonitorTransaction(ActiveTransaction active) {
            this.active = active;
        }

        @Override
        public void setStatus(MonitorStatus status) {
            if (status != null) {
                active.status = status;
            }
        }

        @Override
        public void setStatus(Throwable throwable) {
            active.status = MonitorStatus.FAIL;
            if (throwable != null) {
                active.data.put("error", throwable.getClass().getSimpleName());
            }
        }

        @Override
        public void addData(String key, String value) {
            if (key != null && value != null) {
                active.data.put(key, value);
            }
        }

        @Override
        public String getType() {
            return active.type;
        }

        @Override
        public String getName() {
            return active.name;
        }

        @Override
        public void close() {
            if (!completed) {
                completed = true;
                completeTransaction(active);
            }
        }
    }
}
