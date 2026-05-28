package com.kset.monitor.dubbo;

import com.kset.cloud.config.KsetCloudProperties;
import com.kset.monitor.Monitor;
import com.kset.monitor.facade.MonitorStatus;
import com.kset.monitor.facade.MonitorTransaction;
import com.kset.monitor.facade.MonitorTypes;
import com.kset.monitor.interceptor.InvocationContext;
import com.kset.monitor.interceptor.MonitorInterceptorRegistry;
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
        if (!properties.getDubbo().isTracePropagationEnabled()) {
            return invoker.invoke(invocation);
        }

        RpcContext context = RpcContext.getServiceContext();
        String defaultGray = properties.getDubbo().getDefaultGrayTag();
        if (context.isConsumerSide()) {
            Monitor.bindDubboConsumer(new DubboInvocationAttachments(invocation, false), defaultGray);
        } else if (context.isProviderSide()) {
            Monitor.bindDubboProvider(new DubboInvocationAttachments(invocation, true), defaultGray);
        }

        String serviceName = invoker.getInterface().getSimpleName();
        String txName = serviceName + "." + invocation.getMethodName();
        InvocationContext ctx = new InvocationContext("Dubbo", MonitorTypes.RPC, txName);
        MonitorInterceptorRegistry.notifyBefore(ctx);
        try (MonitorTransaction tx = Monitor.newTransaction(MonitorTypes.RPC, txName)) {
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
            MonitorInterceptorRegistry.notifyAfter(ctx, e);
            throw e;
        } finally {
            if (context.isProviderSide()) {
                Monitor.clear();
            }
        }
    }
}
