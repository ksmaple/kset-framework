package com.kset.monitor.plugin;

import com.kset.monitor.Monitor;
import com.kset.monitor.facade.MonitorTypes;
import com.kset.monitor.interceptor.FrameworkInterceptor;
import com.kset.monitor.interceptor.InvocationContext;
import com.kset.monitor.interceptor.MonitorInterceptorRegistry;

/**
 * OkHttp 监控插件：在 {@link com.kset.common.utils.http.KsetHttp} 初始化时注册。
 */
public final class OkHttpMonitorPlugin implements FrameworkInterceptor {

    private static final OkHttpMonitorPlugin INSTANCE = new OkHttpMonitorPlugin();

    private OkHttpMonitorPlugin() {
    }

    public static void register() {
        MonitorInterceptorRegistry.register(INSTANCE);
    }

    public static void unregister() {
        MonitorInterceptorRegistry.unregister(INSTANCE);
    }

    @Override
    public void before(InvocationContext context) {
        if (!MonitorTypes.HTTP_CLIENT.equals(context.getType())) {
            return;
        }
        context.setAttribute("tx", Monitor.newTransaction(MonitorTypes.HTTP_CLIENT, context.getName()));
    }

    @Override
    public void after(InvocationContext context, Throwable error) {
        if (!MonitorTypes.HTTP_CLIENT.equals(context.getType())) {
            return;
        }
        Object txObj = context.getAttributes().get("tx");
        if (txObj instanceof com.kset.monitor.facade.MonitorTransaction tx) {
            if (error != null) {
                tx.setStatus(error);
            } else {
                tx.setStatus(com.kset.monitor.facade.MonitorStatus.SUCCESS);
            }
            tx.close();
        }
    }
}
