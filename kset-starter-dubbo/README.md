# KSet Dubbo Starter

`kset-starter-dubbo` 集成 Apache Dubbo、Nacos 注册与 KSet 标签路由。TraceId 透传与 RPC Transaction 由 `kset-starter-monitor` 的 Dubbo 插件提供。

接入见 [Nacos / Sentinel / Dubbo](../docs/usage/cloud.md)。

## 依赖

```xml
<dependency>
    <groupId>com.kset</groupId>
    <artifactId>kset-starter-dubbo</artifactId>
</dependency>
```

需要链路监控时同时引入：

```xml
<dependency>
    <groupId>com.kset</groupId>
    <artifactId>kset-starter-monitor</artifactId>
</dependency>
```

## 最小配置

```yaml
spring:
  application:
    name: order-service
  cloud:
    nacos:
      discovery:
        server-addr: ${NACOS_ADDR:127.0.0.1:8848}
      config:
        server-addr: ${NACOS_ADDR:127.0.0.1:8848}

dubbo:
  application:
    name: ${spring.application.name}
  registry:
    address: nacos://${NACOS_ADDR:127.0.0.1:8848}
    register-mode: instance
  protocol:
    name: dubbo
    port: -1
```

引入 starter 后，框架会补齐 `dubbo.application.name`、`dubbo.protocol.name`、`dubbo.protocol.port`、`dubbo.consumer.check=false`、`dubbo.registry.register-mode=instance` 等默认值；如果配置了 Nacos 地址，会补齐 `dubbo.registry.address`。

## 本地 / 远程服务引用

注解由 `kset-common` 独立提供；宿主机只需本地注入时无需引入本 starter。在 Spring Bean 的接口字段上使用 `com.kset.common.service.KsetReference`，无需同时声明 `@Autowired` 或 `@DubboReference`：

```java
@KsetReference(group = "orders", version = "1.0")
private OrderService orderService;
```

启动时若容器内存在该接口的 Spring Bean，按 Spring 的 `@Primary` / `@Qualifier` 规则注入本地实现；否则仅在宿主机引入本 starter 且配置了 `dubbo.registry.address` 时创建并复用 Dubbo 引用。多个本地实现且无法唯一确定，或既无本地实现又无可用远端适配器时，启动失败。`group`、`version` 支持 Spring 配置占位符，不参与本地 Bean 选择，用于远端引用及冲突校验；选择在启动时完成，不因调用失败自动切换。注解只支持非静态、非 `final` 的接口字段。

宿主机未引入本 starter，或未配置 Dubbo 注册中心时，本地实现直接可用，不创建 Dubbo 引用或探测远端。以后引入本 starter、配置注册中心并重启应用会重新选择；只要本地实现仍存在，仍优先使用本地实现。已配置注册中心且选中本地实现时，应用启动阶段按接口、`group`、`version` 检查提供者；若其他进程也提供同一服务，启动失败并提示注入字段，本机已导出的同一服务不算冲突。校验最多等待约 2 秒接收提供者列表，无法读取 Dubbo 目录或注册中心时也会失败。

如果应用使用 Nacos 配置但暂不接入 Dubbo，可显式设置 `dubbo.registry.address: N/A` 保持本地模式；接入 Dubbo 时移除该值并重启应用。

外部业务默认用法与例外边界见[业务服务引用规范](../docs/usage/service-reference.md)。

## 灰度路由

```yaml
kset:
  cloud:
    dubbo:
      gray-enabled: true
      gray-metadata-key: version
      default-gray-tag: stable
```

路由规则默认从 Nacos dataId `{app}-route-rules` 加载：

```json
{
  "conditions": [
    { "tag": "v2", "weight": 10 },
    { "tag": "stable", "weight": 90 }
  ]
}
```

## 边界

- Dubbo 治理与标签路由在本 starter。
- TraceId、spanId、RPC Transaction 在 `kset-starter-monitor`。
- 登录上下文透传在 `kset-starter-auth`。
