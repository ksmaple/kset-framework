package com.kset.monitor.interceptor;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 框架拦截器注册表（插件通过此类注册，无需 Spring 上下文）。
 */
public final class MonitorInterceptorRegistry {

    private static final List<FrameworkInterceptor> INTERCEPTORS = new CopyOnWriteArrayList<>();

    private MonitorInterceptorRegistry() {
    }

    public static void register(FrameworkInterceptor interceptor) {
        if (interceptor != null) {
            INTERCEPTORS.add(interceptor);
        }
    }

    public static void unregister(FrameworkInterceptor interceptor) {
        INTERCEPTORS.remove(interceptor);
    }

    public static List<FrameworkInterceptor> all() {
        return List.copyOf(INTERCEPTORS);
    }

    public static void notifyBefore(InvocationContext context) {
        for (FrameworkInterceptor interceptor : INTERCEPTORS) {
            interceptor.before(context);
        }
    }

    public static void notifyAfter(InvocationContext context, Throwable error) {
        for (FrameworkInterceptor interceptor : INTERCEPTORS) {
            interceptor.after(context, error);
        }
    }
}
