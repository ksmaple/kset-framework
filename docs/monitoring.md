# KSet 全链路监控与门面层

## 依赖

```xml
<dependency>
    <groupId>com.kset</groupId>
    <artifactId>kset-starter-monitor</artifactId>
</dependency>
```

引入后 `kset.monitor.enabled=true`（默认）自动装配链路透传与 CAT 风格埋点；门面由 `KsetMonitorFacadeAutoConfiguration` 注册。

## 目录对照（monitor-facade 参考实现）

| 参考目录 | KSet 落地 |
|----------|-----------|
| `monitor-facade/facade` | `kset-common` → `com.kset.monitor.facade.*` |
| `monitor-facade/backend` | SPI 在 common；`LogBackend` 等在 `kset-starter-monitor` |
| `monitor-facade/interceptor` | `com.kset.monitor.interceptor.*` |
| `monitor-facade/reporter` | `com.kset.monitor.reporter.*` |
| `monitor-spring-boot-starter` | `kset-starter-monitor` |
| `monitor-plugin-dubbo` | `kset-starter-monitor` Dubbo Filter |
| `monitor-plugin-redis` | `kset-starter-redis` `RedisMonitorPluginAutoConfiguration` |
| `monitor-plugin-okhttp` | `kset-common` `OkHttpMonitorPlugin` |

## 统一门面

| API | 用途 |
|-----|------|
| `Monitor.currentTraceId()` | 业务代码获取当前 traceId（推荐） |
| `Monitor.newTransaction(type, name)` | CAT 风格 Transaction |
| `Monitor.logEvent(type, name, status, data)` | 点状事件 |
| `Monitor.logMetric(name, value, kind)` | 指标 |
| `Monitor.logError(t, message)` | 异常上报 |
| `Monitor.bindHttpIncoming(...)` | Servlet 入站 |
| `Monitor.capture()` / `openScope()` | 异步/线程池传递 |

`KsetMonitor` 为兼容别名（`@Deprecated`），委托 `Monitor`。

### CAT 对照

| CAT | KSet |
|-----|------|
| `Cat.newTransaction(type, name)` | `Monitor.newTransaction(type, name)` |
| `transaction.complete()` | `try-with-resources` / `tx.close()` |
| `Cat.logEvent` | `Monitor.logEvent` |
| `Cat.logMetricForCount/Duration` | `Monitor.logMetric(..., COUNT/DURATION)` |
| `Cat.logError` | `Monitor.logError` |

标准 type：`URL`、`SQL`、`RPC`、`Cache`、`MQ`、`Biz`（见 `MonitorTypes`）。

### 业务埋点示例

```java
try (var tx = Monitor.newTransaction(MonitorTypes.BIZ, "createOrder")) {
    // ...
    tx.setStatus(MonitorStatus.SUCCESS);
} catch (Exception e) {
    tx.setStatus(e);
    Monitor.logError(e, "createOrder failed");
    throw e;
}
```

或使用注解 `@Monitored`（避免与 `Monitor` 门面类同名）：

```java
@Monitored(type = "Biz", name = "createOrder")
public void createOrder(...) { }
```

## 无感知能力矩阵

| 能力 | 条件 | 说明 |
|------|------|------|
| Servlet TraceId + URL Transaction | monitor + Servlet | `MvcMonitorInterceptor`（无 MVC 时回退 `TraceIdFilter`） |
| Servlet 灰度 MDC | monitor + Servlet | `GrayTagServletFilter` |
| Dubbo 透传 + RPC Transaction | monitor + Dubbo | `DubboTraceFilter` |
| Gateway TraceId | monitor + Gateway | `TraceIdGatewayFilter` |
| MyBatis SQL Transaction | monitor + MyBatis | `MybatisMonitorInterceptor` |
| `@Monitored` AOP | monitor + AOP | `MonitorAspect` |
| 日志 traceId | KSet Logback | `%X{traceId}` |
| 线程池 MDC | monitor | `MdcThreadPoolTraceAdapter` |
| Redis 插件 | monitor + redis starter | `RedisMonitorPluginAutoConfiguration` |

HTTP 慢请求由 URL Transaction + `LogBackend` 超阈值 WARN 统一处理（不再默认注册 `SlowHttpMonitorFilter`）。

## 配置示例

```yaml
kset:
  monitor:
    enabled: true
    backend: log          # log | cat | skywalking | prometheus（后三者 Phase 2）
    sampler:
      rate: 1.0
    reporter:
      async-enabled: true
      queue-capacity: 2048
    slow-log:
      transaction-warn-ms: 500
    servlet:
      trace-enabled: true
    mybatis:
      enabled: true
    aop:
      enabled: true
    plugin:
      redis:
        enabled: true
```

## 扩展自定义门面

```java
@Bean
@ConditionalOnMissingBean
public KsetMonitorFacade customMonitorFacade() {
    return new MyOtelMonitorFacade();
}
```

Spring 启动时 `Monitor.install(facade)` 会替换默认占位实现。

## 框架插件

通过 `MonitorInterceptorRegistry.register(FrameworkInterceptor)` 注册；OkHttp 可调用 `OkHttpMonitorPlugin.register()`。
