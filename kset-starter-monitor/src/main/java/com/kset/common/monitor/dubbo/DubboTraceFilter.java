package com.kset.common.monitor.dubbo;

import com.kset.cloud.config.KsetCloudProperties;
import com.kset.common.monitor.Monitor;
import com.kset.common.monitor.TraceSnapshot;
import com.kset.common.monitor.facade.MonitorStatus;
import com.kset.common.monitor.facade.MonitorTransaction;
import com.kset.common.monitor.facade.MonitorTypes;
import com.kset.common.monitor.interceptor.InvocationContext;
import com.kset.common.monitor.interceptor.MonitorInterceptorRegistry;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcException;

/**
 * Dubbo TraceId / 灰度标签透传 + RPC Transaction Filter。
 */
public class DubboTraceFilter implements Filter {

    private final KsetCloudProperties properties;

    public DubboTraceFilter(KsetCloudProperties properties) {
        this.properties = properties;
    }

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        RpcContext context = RpcContext.getServiceContext();
        TraceSnapshot previous = Monitor.capture();
        String side = resolveSide(context);
        boolean tracePropagationEnabled = properties.getDubbo().isTracePropagationEnabled();
        if (tracePropagationEnabled) {
            String defaultGray = properties.getDubbo().getDefaultGrayTag();
            if ("consumer".equals(side)) {
                Monitor.bindDubboConsumer(new DubboInvocationAttachments(invocation, false), defaultGray);
            } else if ("provider".equals(side)) {
                Monitor.bindDubboProvider(new DubboInvocationAttachments(invocation, true), defaultGray);
            }
        }

        String serviceName = invoker.getInterface().getSimpleName();
        String txName = serviceName + "." + invocation.getMethodName();
        String txType = resolveTransactionType(side);
        InvocationContext ctx = new InvocationContext("Dubbo", txType, txName);
        ctx.setAttribute("component", "dubbo");
        ctx.setAttribute("side", side);
        ctx.setAttribute("service", invoker.getInterface().getName());
        ctx.setAttribute("method", invocation.getMethodName());
        MonitorInterceptorRegistry.notifyBefore(ctx);
        MonitorTransaction tx = Monitor.newTransaction(txType, txName);
        try {
            tx.addData("component", "dubbo");
            tx.addData("side", side);
            tx.addData("service", invoker.getInterface().getName());
            tx.addData("method", invocation.getMethodName());
            Result result = invoker.invoke(invocation);
            if (result.hasException()) {
                tx.setStatus(result.getException());
                Monitor.logError(result.getException(), txName);
            } else {
                tx.setStatus(MonitorStatus.SUCCESS);
            }
            MonitorInterceptorRegistry.notifyAfter(ctx, result.hasException() ? result.getException() : null);
            return result;
        } catch (RpcException e) {
            tx.setStatus(e);
            tx.addData("errorType", e.getClass().getSimpleName());
            Monitor.logError(e, txName);
            MonitorInterceptorRegistry.notifyAfter(ctx, e);
            throw e;
        } catch (RuntimeException | Error e) {
            tx.setStatus(e);
            tx.addData("errorType", e.getClass().getSimpleName());
            Monitor.logError(e, txName);
            MonitorInterceptorRegistry.notifyAfter(ctx, e);
            throw e;
        } finally {
            tx.close();
            Monitor.restore(previous);
        }
    }

    static String resolveSide(RpcContext context) {
        if (context != null && context.isConsumerSide()) {
            return "consumer";
        }
        if (context != null && context.isProviderSide()) {
            return "provider";
        }
        return "unknown";
    }

    static String resolveTransactionType(String side) {
        if ("consumer".equals(side)) {
            return MonitorTypes.RPC_CONSUMER;
        }
        if ("provider".equals(side)) {
            return MonitorTypes.RPC_PROVIDER;
        }
        return MonitorTypes.RPC;
    }
}
