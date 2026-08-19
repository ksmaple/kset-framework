# Nacos / Sentinel / Dubbo

三个 Starter 解耦，按场景显式组合。不要指望 `kset-starter-nacos` 带上 Web 或 Sentinel。

## Nacos

```xml
<dependency>
    <groupId>com.kset</groupId>
    <artifactId>kset-starter-nacos</artifactId>
</dependency>
```

```yaml
spring:
  application:
    name: order-service
  cloud:
    nacos:
      discovery:
        server-addr: ${NACOS_ADDR:127.0.0.1:8848}
        namespace: dev
        group: KSET_GROUP
      config:
        server-addr: ${NACOS_ADDR:127.0.0.1:8848}
        namespace: dev
        group: KSET_GROUP
  config:
    import: optional:nacos:${spring.application.name}.yaml
```

框架会补 group / namespace，并追加公共配置 `optional:nacos:kset-common.yaml`。

## Sentinel

规则默认按应用名从 Nacos 加载：

```yaml
kset:
  cloud:
    sentinel:
      enabled: true
      flow-rule-data-id: order-service-flow-rules
      degrade-rule-data-id: order-service-degrade-rules
```

Gateway 侧限流不走这个 Starter，由 `kset-starter-gateway` 处理。

## Dubbo

```yaml
dubbo:
  application:
    name: ${spring.application.name}
  registry:
    address: nacos://${NACOS_ADDR:127.0.0.1:8848}
    register-mode: instance
  protocol:
    name: dubbo
    port: -1
kset:
  cloud:
    dubbo:
      gray-enabled: true
      gray-metadata-key: version
      default-gray-tag: stable
```

TraceId 与 RPC Transaction 在 `kset-starter-monitor`；登录上下文透传在 `kset-starter-auth`。

## Nacos dataId 约定

| 用途 | dataId |
|------|--------|
| 应用主配置 | `{app}.yaml` |
| 公共配置 | `kset-common.yaml` |
| Sentinel 限流 / 熔断 / 热点 | `{app}-flow-rules` / `{app}-degrade-rules` / `{app}-param-flow-rules` |
| Dubbo 路由 | `{app}-route-rules` |
| Gateway 路由 / 限流 | `{gateway-app}-gateway-routes` / `{gateway-app}-gateway-flow-rules` |

JSON 样例见对应模块 README。自定义规则变更实现 `CloudRuleProvider`；灰度标签实现 `GrayTagResolver`。
