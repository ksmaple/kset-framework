package com.kset.monitor.interceptor;

import com.kset.common.monitor.HttpTraceBinding;
import com.kset.monitor.Monitor;
import com.kset.common.trace.TraceHeaders;
import com.kset.monitor.facade.MonitorStatus;
import com.kset.monitor.facade.MonitorTransaction;
import com.kset.monitor.facade.MonitorTypes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Spring MVC 监控拦截器：URL Transaction + 慢请求检测（合并原 TraceIdFilter 慢 HTTP 能力）。
 */
public class MvcMonitorInterceptor implements HandlerInterceptor {

    private static final String TX_ATTRIBUTE = "kset.monitor.mvc.transaction";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (Monitor.currentTraceId().isEmpty()) {
            String incomingTraceId = request.getHeader(TraceHeaders.TRACE_ID_HEADER);
            HttpTraceBinding binding = Monitor.bindHttpIncoming(incomingTraceId);
            response.setHeader(binding.getTraceIdHeaderName(), binding.getTraceId());
            response.setHeader(binding.getSpanIdHeaderName(), binding.getSpanId());
        }
        String txName = request.getMethod() + " " + request.getRequestURI();
        MonitorTransaction tx = Monitor.newTransaction(MonitorTypes.URL, txName);
        request.setAttribute(TX_ATTRIBUTE, tx);
        InvocationContext ctx = new InvocationContext("MVC", MonitorTypes.URL, txName);
        ctx.setAttribute("method", request.getMethod());
        ctx.setAttribute("uri", request.getRequestURI());
        MonitorInterceptorRegistry.notifyBefore(ctx);
        request.setAttribute("kset.monitor.mvc.context", ctx);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        Object ctxObj = request.getAttribute("kset.monitor.mvc.context");
        if (ctxObj instanceof InvocationContext ctx) {
            MonitorInterceptorRegistry.notifyAfter(ctx, ex);
        }
        Object txObj = request.getAttribute(TX_ATTRIBUTE);
        if (txObj instanceof MonitorTransaction tx) {
            if (ex != null) {
                tx.setStatus(ex);
                Monitor.logError(ex, tx.getName());
            } else {
                tx.setStatus(MonitorStatus.SUCCESS);
            }
            tx.close();
        }
        Monitor.clear();
    }
}
