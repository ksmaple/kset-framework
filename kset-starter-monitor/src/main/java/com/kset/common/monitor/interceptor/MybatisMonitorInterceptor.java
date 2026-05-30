package com.kset.common.monitor.interceptor;

import com.kset.common.monitor.Monitor;
import com.kset.common.monitor.facade.MonitorStatus;
import com.kset.common.monitor.facade.MonitorTransaction;
import com.kset.common.monitor.facade.MonitorTypes;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

/**
 * MyBatis SQL Transaction 拦截器。
 */
@Intercepts({
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}),
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class})
})
public class MybatisMonitorInterceptor implements Interceptor {

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        MappedStatement ms = (MappedStatement) invocation.getArgs()[0];
        String name = ms.getId();
        InvocationContext ctx = new InvocationContext("Mybatis", MonitorTypes.SQL, name);
        ctx.setAttribute("component", "mybatis");
        ctx.setAttribute("sqlId", name);
        MonitorInterceptorRegistry.notifyBefore(ctx);
        MonitorTransaction tx = Monitor.newTransaction(MonitorTypes.SQL, name);
        try {
            tx.addData("component", "mybatis");
            tx.addData("sqlId", name);
            Object result = invocation.proceed();
            tx.setStatus(MonitorStatus.SUCCESS);
            MonitorInterceptorRegistry.notifyAfter(ctx, null);
            return result;
        } catch (Throwable e) {
            tx.setStatus(e);
            tx.addData("errorType", e.getClass().getSimpleName());
            Monitor.logError(e, name);
            MonitorInterceptorRegistry.notifyAfter(ctx, e);
            throw e;
        } finally {
            tx.close();
        }
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }
}
