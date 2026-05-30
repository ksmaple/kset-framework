# KSet 全链路监控与门面层

## 依赖

```xml
<dependency>
    <groupId>com.kset</groupId>
    <artifactId>kset-starter-monitor</artifactId>
</dependency>
```

引入后 `kset.monitor.enabled=true`（默认）自动装配链路透传与 CAT 风格埋点；门面由 `KsetMonitorFacadeAutoConfiguration` 注册，**默认后端为 `LogBackend`（本地 SLF4J 日志）**。只有显式配置 `kset.monitor.backend=cat` 时才启用 CAT 后端。

## 目录对照（monitor-facade 参考实现）

| 参考目录 | KSet 落地 |
|----------|-----------|
| `monitor-facade/facade` | `kset-common` → `com.kset.common.monitor.facade.*` |
| `monitor-facade/backend` | `LogBackend` 在 `kset-common`；远程后端占位在 `kset-starter-monitor` |
| `monitor-facade/interceptor` | `com.kset.common.monitor.interceptor.*` |
| `monitor-facade/reporter` | `com.kset.common.monitor.reporter.*` |
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

统一入口类：`com.kset.common.monitor.Monitor`。

**仅依赖 `kset-common`（未引入 starter-monitor）时**：已内置 `DefaultMonitorFacade` + `LogBackend`，`newTransaction` / `logEvent` / `logError` 等会写入本地 SLF4J；traceId 需业务调用 `Monitor.setTraceId` 或 `bindHttpIncoming` 自行绑定。

**引入 `kset-starter-monitor` 后**：Spring 启动时 `Monitor.install(...)` 替换为可配置门面（采样、同步调用后端、Servlet/Dubbo/Gateway 无感知集成）。门面层不创建异步上报队列；CAT、日志或其他后端是否异步由具体后端框架自行决定。

## 下游升级对照

自 `kset-framework` 监控包合并后，业务工程须同步替换 import 与调用：

| 旧（已移除） | 新 |
|--------------|-----|
| `com.kset.monitor.Monitor` | `com.kset.common.monitor.Monitor` |
| `com.kset.common.monitor.KsetMonitor` | `com.kset.common.monitor.Monitor` |
| `com.kset.common.monitor.KsetMonitorFacade` | `com.kset.common.monitor.facade.MonitorFacade` |
| `com.kset.monitor.facade.*` | `com.kset.common.monitor.facade.*` |
| `com.kset.monitor.aop.Monitored` | `com.kset.common.monitor.aop.Monitored` |

`@ConditionalOnClass` / `@ConditionalOnMissingClass` 中的全限定类名字符串须一并替换，例如 `com.kset.common.monitor.Monitor`、`com.kset.common.monitor.interceptor.MybatisMonitorInterceptor`。

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
| MyBatis SQL Transaction | monitor + MyBatis / MyBatis-Plus | `KsetMonitorMybatisAutoConfiguration` 注册 `MybatisMonitorInterceptor` |
| `@Monitored` AOP | monitor + AOP | `MonitorAspect` |
| 日志 traceId | KSet Logback | `%X{traceId}`、`%X{spanId}`（MDC 由 Monitor 写入） |
| 日志 operator / flow | KSet Logback | `%X{operator}`、`flow.*`（由 OpLogContext / FlowLogContext 写入） |
| 线程池 MDC | monitor | `MdcThreadPoolTraceAdapter` |
| Redis 插件 | monitor + redis starter | `RedisMonitorPluginAutoConfiguration` |

HTTP 慢请求由 URL Transaction + `LogBackend` 超阈值 WARN 统一处理。

数据源组件兼容：业务引入 `kset-starter-datasource` 和对应 JDBC 驱动后即可使用 MyBatis-Plus。只要业务同时引入 `kset-starter-monitor`，SQL Transaction 拦截器会在 MyBatis 自动装配前注册，无需按数据库类型额外配置。

## 配置示例

```yaml
kset:
  monitor:
    enabled: true
    backend: log          # 默认 log=本地 SLF4J；cat=CAT 后端，未配置 cat 时不启动 CAT
    cat:
      initialize: false   # 需要框架主动初始化 CAT 时设 true；否则由业务已有 CAT 配置负责
      domain: demo-app    # initialize=true 时可选
    sampler:
      rate: 1.0
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
import com.kset.common.monitor.Monitor;
import com.kset.common.monitor.facade.MonitorFacade;
import com.kset.common.monitor.facade.MonitorTypes;
import com.kset.common.monitor.facade.MonitorStatus;

@Bean
@ConditionalOnMissingBean
public MonitorFacade customMonitorFacade() {
    return new MyOtelMonitorFacade();
}
```

Spring 启动时 `Monitor.install(facade)` 会替换默认占位实现。

## 框架插件

通过 `MonitorInterceptorRegistry.register(FrameworkInterceptor)` 注册；OkHttp 可调用 `OkHttpMonitorPlugin.register()`。

## 与 logging 协作

分布式 trace 与业务日志通过 **SLF4J MDC** 衔接，职责分离如下：

| 维度 | 归属 | MDC 键 | 写入方 | 消费方 |
|------|------|--------|--------|--------|
| 请求级链路 | `com.kset.common.monitor` | `traceId`, `spanId`, `grayTag` | `Monitor` / Filter | logback `%X{...}`、StructLog JSON |
| 操作人 | `com.kset.common.logging` | `operator` | `OpLogContext` | logback、操作审计日志 |
| 业务流程步骤 | `com.kset.common.logging` | `flow.instanceId`, `flow.step` 等 | `FlowLogContext` | logback、流程完整性分析 |

**规则：**

- 业务代码获取 traceId 用 `Monitor.currentTraceId()`，勿在 logging 包自建 TraceContext
- `FlowLogContext.clear()` 只清 `flow.*`，不调用 `Monitor.clear()`
- 线程池传播用 `MdcThreadPoolTraceAdapter` + `Monitor.capture()` / `openScope()`
- `StructLog` 不主动 `setTraceId`；traceId 由 MDC 自动进入 JSON 日志

```java
// 结构化业务日志：traceId 自动从 MDC 进 JSON
LOG.info("order created", "orderId", orderId);

// 长流程步骤（与 Monitor traceId 共存）
String flowId = FlowLogContext.beginFlow("document_upload", userId);
try {
    FlowLogContext.step("validate", FlowEventType.ENTER);
} finally {
    FlowLogContext.clear();
}
```

logback 配置见 `kset-common` 内 `kset-logback-spring.xml`（dev 文本）与 `kset-logback-file-appenders.xml`（test/prod JSON）。
