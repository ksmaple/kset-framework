package com.kset.common.utils.http;

import com.kset.common.monitor.Monitor;
import com.kset.common.monitor.TraceSnapshot;
import com.kset.common.monitor.facade.MonitorStatus;
import com.kset.common.monitor.facade.MonitorTransaction;
import com.kset.common.monitor.facade.MonitorTypes;
import com.kset.common.trace.TraceHeaders;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import lombok.extern.slf4j.Slf4j;
import okio.Buffer;
import okio.BufferedSource;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
public class HttpLogInterceptor implements Interceptor {

    private static final Charset UTF8 = StandardCharsets.UTF_8;
    private static volatile boolean monitorEnabled = true;

    public static void setMonitorEnabled(boolean enabled) {
        monitorEnabled = enabled;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        TraceSnapshot previous = Monitor.capture();
        if (monitorEnabled) {
            request = withTraceHeaders(request);
        }
        try {
            return doIntercept(chain, request);
        } finally {
            if (monitorEnabled) {
                Monitor.restore(previous);
            }
        }
    }

    private static Response doIntercept(Chain chain, Request request) throws IOException {
        String reqBody = readRequestBody(request.body());

        if (log.isDebugEnabled()) {
            log.debug("http req method={} url={} headers={} body={}",
                    request.method(), request.url(), request.headers(), reqBody);
        }

        if (!monitorEnabled) {
            long startNs = System.nanoTime();
            Response response = chain.proceed(request);
            long tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
            logResponse(response, tookMs);
            return response;
        }

        String txName = request.method() + " " + request.url().encodedPath();
        MonitorTransaction tx = Monitor.newTransaction(MonitorTypes.HTTP_CLIENT, txName);
        tx.addData("component", "http-client");
        tx.addData("method", request.method());
        tx.addData("host", request.url().host());
        tx.addData("path", request.url().encodedPath());
        long startNs = System.nanoTime();
        Response response;
        long tookMs;
        try {
            response = chain.proceed(request);
            tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
            tx.addData("status", String.valueOf(response.code()));
            tx.setStatus(MonitorStatus.SUCCESS);
        } catch (IOException e) {
            tx.setStatus(e);
            tx.addData("errorType", e.getClass().getSimpleName());
            Monitor.logError(e, txName);
            throw e;
        } finally {
            tx.close();
        }

        ResponseBody responseBody = response.body();
        String rspBody = responseBody != null ? readResponseBody(responseBody) : null;

        logResponse(response, tookMs, rspBody);

        return response;
    }

    private static Request withTraceHeaders(Request request) {
        String traceId = Monitor.currentTraceId().orElseGet(() -> {
            String generated = Monitor.generateTraceId();
            Monitor.setTraceId(generated);
            return generated;
        });
        String spanId = Monitor.currentSpanId().orElseGet(() -> {
            String generated = Monitor.generateSpanId();
            Monitor.setSpanId(generated);
            return generated;
        });
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
            Monitor.setTraceId(traceId);
        }
        if (spanId == null || spanId.isBlank()) {
            spanId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            Monitor.setSpanId(spanId);
        }
        Request.Builder builder = request.newBuilder()
                .header(TraceHeaders.TRACE_ID_HEADER, traceId)
                .header(TraceHeaders.SPAN_ID_HEADER, spanId);
        Monitor.currentGrayTag().ifPresent(grayTag -> builder.header(TraceHeaders.GRAY_TAG_HEADER, grayTag));
        return builder.build();
    }

    private static void logResponse(Response response, long tookMs) throws IOException {
        ResponseBody responseBody = response.body();
        String rspBody = responseBody != null ? readResponseBody(responseBody) : null;
        logResponse(response, tookMs, rspBody);
    }

    private static void logResponse(Response response, long tookMs, String rspBody) {
        if (log.isDebugEnabled()) {
            log.debug("http rsp code={} url={} tookMs={} body={}",
                    response.code(), response.request().url(), tookMs, rspBody);
        }
    }

    private static String readRequestBody(RequestBody requestBody) throws IOException {
        if (requestBody == null) {
            return null;
        }
        Buffer buffer = new Buffer();
        requestBody.writeTo(buffer);
        return buffer.readString(charset(requestBody.contentType()));
    }

    private static String readResponseBody(ResponseBody responseBody) throws IOException {
        BufferedSource source = responseBody.source();
        source.request(Long.MAX_VALUE);
        Buffer buffer = source.buffer();
        return buffer.clone().readString(charset(responseBody.contentType()));
    }

    private static Charset charset(MediaType contentType) {
        if (contentType == null) {
            return UTF8;
        }
        try {
            Charset resolved = contentType.charset(UTF8);
            return resolved != null ? resolved : UTF8;
        } catch (UnsupportedCharsetException e) {
            log.warn("Unsupported charset in content-type: {}", contentType, e);
            return UTF8;
        }
    }
}
