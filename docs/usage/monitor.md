# 监控与 TraceId

依赖 `kset-starter-monitor`。默认后端是本地日志；显式 `kset.monitor.backend=cat` 才走 CAT。

业务埋点统一用 `com.kset.common.monitor.Monitor`，不要自建 TraceContext。

```java
try (var tx = Monitor.newTransaction(MonitorTypes.BIZ, "createOrder")) {
    // 业务逻辑
    tx.setStatus(MonitorStatus.SUCCESS);
} catch (Exception e) {
    Monitor.logError(e, "createOrder failed");
    throw e;
}
```

或方法上 `@Monitored(type = "Biz", name = "createOrder")`。

## 跨线程

```java
TraceSnapshot context = Monitor.capture();
executor.execute(() -> {
    try (MonitorScope scope = Monitor.openScope(context)) {
        asyncWork();
    }
});
```

`Parallel` / `EventFacade` 本地异步已经会带上调用线程的 Trace。线程池也可挂 `MdcThreadPoolTraceAdapter`。

## 无感知范围

引入 starter 后，Servlet、Dubbo、Gateway、MyBatis、KSet Redis 门面、MQ 事件路径会自动打 Transaction。原生 `RedisTemplate` / `RedissonClient` 不会被包装。

```yaml
kset:
  monitor:
    enabled: true
    backend: log
    servlet:
      trace-enabled: true
    mybatis:
      enabled: true
    aop:
      enabled: true
    redis:
      enabled: true
```

仅依赖 `kset-common`、不引 starter 时，`Monitor` 仍可写本地日志，但需要业务自己 `setTraceId` / `bindHttpIncoming`。

配置与 CAT 对照见 [kset-starter-monitor/README.md](../../kset-starter-monitor/README.md)。
