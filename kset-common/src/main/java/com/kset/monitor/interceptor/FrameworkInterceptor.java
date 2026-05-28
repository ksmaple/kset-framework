package com.kset.monitor.interceptor;

/**
 * 框架监控扩展点（Dubbo / Redis / OkHttp 等插件注册）。
 */
public interface FrameworkInterceptor {

    void before(InvocationContext context);

    void after(InvocationContext context, Throwable error);
}
