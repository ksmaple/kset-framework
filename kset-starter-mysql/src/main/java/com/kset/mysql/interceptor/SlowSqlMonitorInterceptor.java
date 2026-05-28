package com.kset.mysql.interceptor;

import com.kset.monitor.Monitor;
import com.kset.monitor.facade.MonitorStatus;
import com.kset.monitor.facade.MonitorTransaction;
import com.kset.monitor.facade.MonitorTypes;
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
 * 慢 SQL 监控（经 {@link Monitor#newTransaction}，默认阈值 200ms 由 LogBackend 判定 WARN）。
 *
 * @deprecated 请使用 {@code kset-starter-monitor} 的 {@code MybatisMonitorInterceptor}。
 */
@Deprecated
@Intercepts({
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}),
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class})
})
public class SlowSqlMonitorInterceptor implements Interceptor {

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        MappedStatement ms = (MappedStatement) invocation.getArgs()[0];
        try (MonitorTransaction tx = Monitor.newTransaction(MonitorTypes.SQL, ms.getId())) {
            Object result = invocation.proceed();
            tx.setStatus(MonitorStatus.SUCCESS);
            return result;
        } catch (Throwable e) {
            throw e;
        }
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }
}
