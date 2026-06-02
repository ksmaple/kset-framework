package com.kset.common.monitor;

import com.kset.common.context.KsetContext;
import com.kset.common.context.KsetContextKey;
import com.kset.common.context.KsetContextKeys;
import com.kset.common.monitor.backend.LogBackend;
import com.kset.common.monitor.facade.MetricKind;
import com.kset.common.monitor.facade.MonitorFacade;
import com.kset.common.monitor.facade.MonitorStatus;
import com.kset.common.monitor.facade.MonitorTransaction;
import com.kset.common.monitor.internal.DefaultMonitorFacade;
import com.kset.common.monitor.internal.NoOpMonitorFacade;
import com.kset.common.monitor.internal.NoOpMonitorTransaction;
import com.kset.common.monitor.reporter.NoOpMetricAggregator;
import com.kset.common.monitor.sampler.RateSampler;
import com.kset.common.trace.TraceHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.Supplier;


public final class Monitor {

    private static final Logger log = LoggerFactory.getLogger(Monitor.class);

    private static volatile MonitorFacade facade = createBuiltinDefault();

    private Monitor() {
    }

    private static MonitorFacade createBuiltinDefault() {
        return new DefaultMonitorFacade(
                new LogBackend(),
                new RateSampler(1.0),
                new NoOpMetricAggregator());
    }

    public static void install(MonitorFacade newFacade) {
        facade = Objects.requireNonNull(newFacade, "facade");
    }

    public static MonitorFacade facade() {
        return facade;
    }

    public static Optional<String> currentTraceId() {
        return KsetContext.get(KsetContextKeys.TRACE_ID)
                .or(() -> safeGet("currentTraceId", facade::currentTraceId, Monitor::localTraceId, Optional.empty()));
    }

    public static Optional<String> currentSpanId() {
        return KsetContext.get(KsetContextKeys.SPAN_ID)
                .or(() -> safeGet("currentSpanId", facade::currentSpanId, Monitor::localSpanId, Optional.empty()));
    }

    public static Optional<String> currentGrayTag() {
        return KsetContext.get(KsetContextKeys.GRAY_TAG)
                .or(() -> safeGet("currentGrayTag", facade::currentGrayTag, Monitor::localGrayTag, Optional.empty()));
    }

    public static String generateTraceId() {
        return safeGet("generateTraceId", facade::generateTraceId, Monitor::localGenerateTraceId, "");
    }

    public static String generateSpanId() {
        return safeGet("generateSpanId", facade::generateSpanId, Monitor::localGenerateSpanId, "");
    }

    public static HttpTraceBinding bindHttpIncoming(String incomingTraceId) {
        return safeGet("bindHttpIncoming",
                () -> facade.bindHttpIncoming(incomingTraceId),
                () -> localBindHttpIncoming(incomingTraceId),
                new HttpTraceBinding(firstNonBlank(incomingTraceId, ""), ""));
    }

    public static void bindHttpGrayTag(String incomingGrayTag, String defaultGray) {
        safeRun("bindHttpGrayTag",
                () -> facade.bindHttpGrayTag(incomingGrayTag, defaultGray),
                () -> localSet(TraceHeaders.GRAY_TAG_KEY, firstNonBlank(incomingGrayTag, defaultGray)));
    }

    public static void clearHttpGrayTag() {
        safeRun("clearHttpGrayTag", facade::clearHttpGrayTag, () -> MDC.remove(TraceHeaders.GRAY_TAG_KEY));
        KsetContext.remove(KsetContextKeys.GRAY_TAG);
    }

    public static void bindDubboConsumer(DubboAttachmentAccessor attachments, String defaultGray) {
        safeRun("bindDubboConsumer",
                () -> facade.bindDubboConsumer(attachments, defaultGray),
                () -> localBindDubboConsumer(attachments, defaultGray));
    }

    public static void bindDubboProvider(DubboAttachmentAccessor attachments, String defaultGray) {
        safeRun("bindDubboProvider",
                () -> facade.bindDubboProvider(attachments, defaultGray),
                () -> localBindDubboProvider(attachments, defaultGray));
    }

    public static GatewayTraceBinding resolveGatewayTrace(String incomingTraceId, String traceHeaderName) {
        return safeGet("resolveGatewayTrace",
                () -> facade.resolveGatewayTrace(incomingTraceId, traceHeaderName),
                () -> localResolveGatewayTrace(incomingTraceId, traceHeaderName),
                new GatewayTraceBinding(firstNonBlank(incomingTraceId, ""), "", traceHeaderName));
    }

    public static Object putReactorContext(Object context, String traceId, String grayTag) {
        return safeGet("putReactorContext",
                () -> facade.putReactorContext(context, traceId, grayTag),
                () -> localPutReactorContext(context, traceId, grayTag),
                context);
    }

    public static Optional<String> getFromReactor(Object contextView, String key) {
        return safeGet("getFromReactor",
                () -> facade.getFromReactor(contextView, key),
                () -> localGetFromReactor(contextView, key),
                Optional.empty());
    }

    public static void setTraceId(String traceId) {
        safeRun("setTraceId", () -> facade.setTraceId(traceId), () -> localSet(TraceHeaders.TRACE_ID_KEY, traceId));
        putTraceContext(KsetContextKeys.TRACE_ID, traceId);
    }

    public static void setSpanId(String spanId) {
        safeRun("setSpanId", () -> facade.setSpanId(spanId), () -> localSet(TraceHeaders.SPAN_ID_KEY, spanId));
        putTraceContext(KsetContextKeys.SPAN_ID, spanId);
    }

    public static void setGrayTag(String grayTag) {
        safeRun("setGrayTag", () -> facade.setGrayTag(grayTag), () -> localSet(TraceHeaders.GRAY_TAG_KEY, grayTag));
        putTraceContext(KsetContextKeys.GRAY_TAG, grayTag);
    }

    public static void clear() {
        safeRun("clear", facade::clear, Monitor::localClear);
        KsetContext.remove(KsetContextKeys.TRACE_ID);
        KsetContext.remove(KsetContextKeys.SPAN_ID);
        KsetContext.remove(KsetContextKeys.GRAY_TAG);
    }

    public static TraceSnapshot capture() {
        TraceSnapshot fallback = new TraceSnapshot(
                KsetContext.get(KsetContextKeys.TRACE_ID).orElse(null),
                KsetContext.get(KsetContextKeys.SPAN_ID).orElse(null),
                KsetContext.get(KsetContextKeys.GRAY_TAG).orElse(null));
        return safeGet("capture", facade::capture, Monitor::localCapture, fallback);
    }

    public static void restore(TraceSnapshot snapshot) {
        safeRun("restore", () -> facade.restore(snapshot), () -> localRestore(snapshot));
        syncTraceContext(snapshot);
    }

    public static MonitorScope openScope(TraceSnapshot snapshot) {
        TraceSnapshot previous = capture();
        restore(snapshot);
        return new MonitorScope(previous);
    }

    public static MonitorTransaction newTransaction(String type, String name) {
        MonitorTransaction transaction = safeGet("newTransaction",
                () -> facade.newTransaction(type, name),
                () -> new NoOpMonitorTransaction(type, name),
                new NoOpMonitorTransaction(type, name));
        return new SafeMonitorTransaction(transaction, type, name);
    }

    public static void logEvent(String type, String name, MonitorStatus status, String data) {
        safeRun("logEvent", () -> facade.logEvent(type, name, status, data));
    }

    public static void logMetric(String name, long value, MetricKind kind) {
        safeRun("logMetric", () -> facade.logMetric(name, value, kind));
    }

    public static void logError(Throwable throwable, String message) {
        safeRun("logError", () -> facade.logError(throwable, message));
    }

    public static void runInTransaction(String type, String name, Runnable action) {
        try (MonitorTransaction tx = newTransaction(type, name)) {
            try {
                action.run();
                tx.setStatus(MonitorStatus.SUCCESS);
            } catch (RuntimeException | Error e) {
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
            } catch (Error e) {
                tx.setStatus(e);
                logError(e, e.getMessage() != null ? e.getMessage() : type + "." + name);
                throw e;
            }
        }
    }

    private static void safeRun(String action, Runnable runnable) {
        safeRun(action, runnable, null);
    }

    private static void safeRun(String action, Runnable runnable, Runnable fallback) {
        try {
            runnable.run();
        } catch (RuntimeException | Error e) {
            logMonitorError(action, e);
            if (fallback != null) {
                runFallback(action, fallback);
            }
        }
    }

    private static <T> T safeGet(String action, Supplier<T> supplier, Supplier<T> fallback, T finalFallback) {
        try {
            T value = supplier.get();
            if (value != null) {
                return value;
            }
            T fallbackValue = fallback.get();
            return fallbackValue != null ? fallbackValue : finalFallback;
        } catch (RuntimeException | Error e) {
            logMonitorError(action, e);
            T fallbackValue = safeFallback(action, fallback);
            return fallbackValue != null ? fallbackValue : finalFallback;
        }
    }

    private static void logMonitorError(String action, Throwable throwable) {
        try {
            log.error("monitor facade failed action={}", action, throwable);
        } catch (RuntimeException | Error ignored) {
            // Logging failure must not affect business flow.
        }
    }

    private static void runFallback(String action, Runnable fallback) {
        try {
            fallback.run();
        } catch (RuntimeException | Error e) {
            logMonitorError(action + ".fallback", e);
        }
    }

    private static <T> T safeFallback(String action, Supplier<T> fallback) {
        try {
            return fallback.get();
        } catch (RuntimeException | Error e) {
            logMonitorError(action + ".fallback", e);
            return null;
        }
    }

    private static Optional<String> localTraceId() {
        return Optional.ofNullable(MDC.get(TraceHeaders.TRACE_ID_KEY));
    }

    private static Optional<String> localSpanId() {
        return Optional.ofNullable(MDC.get(TraceHeaders.SPAN_ID_KEY));
    }

    private static Optional<String> localGrayTag() {
        return Optional.ofNullable(MDC.get(TraceHeaders.GRAY_TAG_KEY));
    }

    private static String localGenerateTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String localGenerateSpanId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private static HttpTraceBinding localBindHttpIncoming(String incomingTraceId) {
        String traceId = firstNonBlank(incomingTraceId, localGenerateTraceId());
        String spanId = localGenerateSpanId();
        localSet(TraceHeaders.TRACE_ID_KEY, traceId);
        localSet(TraceHeaders.SPAN_ID_KEY, spanId);
        return new HttpTraceBinding(traceId, spanId);
    }

    private static GatewayTraceBinding localResolveGatewayTrace(String incomingTraceId, String traceHeaderName) {
        return new GatewayTraceBinding(firstNonBlank(incomingTraceId, localGenerateTraceId()),
                localGenerateSpanId(),
                traceHeaderName);
    }

    private static Object localPutReactorContext(Object context, String traceId, String grayTag) {
        if (!(context instanceof Context reactorContext)) {
            return context;
        }
        Context updated = reactorContext;
        if (traceId != null) {
            updated = updated.put(TraceHeaders.TRACE_ID_KEY, traceId);
        }
        if (grayTag != null) {
            updated = updated.put(TraceHeaders.GRAY_TAG_KEY, grayTag);
        }
        return updated;
    }

    private static Optional<String> localGetFromReactor(Object contextView, String key) {
        if (!(contextView instanceof ContextView view) || key == null || !view.hasKey(key)) {
            return Optional.empty();
        }
        return Optional.ofNullable(view.get(key));
    }

    private static TraceSnapshot localCapture() {
        return new TraceSnapshot(
                currentTraceId().orElse(null),
                currentSpanId().orElse(null),
                currentGrayTag().orElse(null));
    }

    private static void localRestore(TraceSnapshot snapshot) {
        if (snapshot == null) {
            localClear();
            syncTraceContext(null);
            return;
        }
        localClear();
        localSet(TraceHeaders.TRACE_ID_KEY, snapshot.getTraceId());
        localSet(TraceHeaders.SPAN_ID_KEY, snapshot.getSpanId());
        localSet(TraceHeaders.GRAY_TAG_KEY, snapshot.getGrayTag());
        syncTraceContext(snapshot);
    }

    private static void localBindDubboConsumer(DubboAttachmentAccessor attachments, String defaultGray) {
        if (attachments == null) {
            return;
        }
        String traceId = localTraceId().orElseGet(Monitor::localGenerateTraceId);
        String grayTag = localGrayTag().orElse(defaultGray);
        attachments.setAttachment(TraceHeaders.TRACE_ID_KEY, traceId);
        attachments.setAttachment(TraceHeaders.GRAY_TAG_KEY, grayTag);
        localSet(TraceHeaders.TRACE_ID_KEY, traceId);
        localSet(TraceHeaders.GRAY_TAG_KEY, grayTag);
    }

    private static void localBindDubboProvider(DubboAttachmentAccessor attachments, String defaultGray) {
        String traceId = attachments != null ? attachments.getAttachment(TraceHeaders.TRACE_ID_KEY) : null;
        String grayTag = attachments != null ? attachments.getAttachment(TraceHeaders.GRAY_TAG_KEY) : null;
        localSet(TraceHeaders.TRACE_ID_KEY, firstNonBlank(traceId, localGenerateTraceId()));
        localSet(TraceHeaders.GRAY_TAG_KEY, firstNonBlank(grayTag, defaultGray));
    }

    private static void localSet(String key, String value) {
        if (value != null) {
            MDC.put(key, value);
        }
    }

    private static void localClear() {
        MDC.remove(TraceHeaders.TRACE_ID_KEY);
        MDC.remove(TraceHeaders.SPAN_ID_KEY);
        MDC.remove(TraceHeaders.GRAY_TAG_KEY);
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }

    private static void putTraceContext(KsetContextKey<String> key, String value) {
        if (value == null) {
            KsetContext.remove(key);
        } else {
            KsetContext.put(key, value);
        }
    }

    private static void syncTraceContext(TraceSnapshot snapshot) {
        if (snapshot == null) {
            KsetContext.remove(KsetContextKeys.TRACE_ID);
            KsetContext.remove(KsetContextKeys.SPAN_ID);
            KsetContext.remove(KsetContextKeys.GRAY_TAG);
            return;
        }
        putTraceContext(KsetContextKeys.TRACE_ID, snapshot.getTraceId());
        putTraceContext(KsetContextKeys.SPAN_ID, snapshot.getSpanId());
        putTraceContext(KsetContextKeys.GRAY_TAG, snapshot.getGrayTag());
    }

    private static final class SafeMonitorTransaction implements MonitorTransaction {

        private final MonitorTransaction delegate;
        private final String type;
        private final String name;

        private SafeMonitorTransaction(MonitorTransaction delegate, String type, String name) {
            this.delegate = delegate != null ? delegate : new NoOpMonitorTransaction(type, name);
            this.type = type;
            this.name = name;
        }

        @Override
        public void setStatus(MonitorStatus status) {
            safeRun("transaction.setStatus", () -> delegate.setStatus(status));
        }

        @Override
        public void setStatus(Throwable throwable) {
            safeRun("transaction.setStatusThrowable", () -> delegate.setStatus(throwable));
        }

        @Override
        public void addData(String key, String value) {
            safeRun("transaction.addData", () -> delegate.addData(key, value));
        }

        @Override
        public String getType() {
            return safeGet("transaction.getType", delegate::getType, () -> type, type);
        }

        @Override
        public String getName() {
            return safeGet("transaction.getName", delegate::getName, () -> name, name);
        }

        @Override
        public void close() {
            safeRun("transaction.close", delegate::close);
        }
    }
}
