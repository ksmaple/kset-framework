# KSet Sentinel Starter

`kset-starter-sentinel` 集成 Spring Cloud Alibaba Sentinel，并从 Nacos 加载限流、熔断和热点参数规则。

## 依赖

```xml
<dependency>
    <groupId>com.kset</groupId>
    <artifactId>kset-starter-sentinel</artifactId>
</dependency>
```

## 配置

```yaml
spring:
  application:
    name: order-service
  cloud:
    nacos:
      config:
        server-addr: ${NACOS_ADDR:127.0.0.1:8848}
        namespace: dev
        group: KSET_GROUP

kset:
  cloud:
    sentinel:
      enabled: true
      flow-rule-data-id: order-service-flow-rules
      degrade-rule-data-id: order-service-degrade-rules
      param-flow-rule-data-id: order-service-param-flow-rules
```

`*-data-id` 未配置时，框架按 `spring.application.name` 自动生成。

## 规则格式

限流规则使用 Sentinel 标准 JSON 数组：

```json
[
  {
    "resource": "/api/orders",
    "grade": 1,
    "count": 100,
    "strategy": 0,
    "controlBehavior": 0
  }
]
```

## 边界

- 本 starter 不提供 Gateway Sentinel 网关规则；Gateway 侧规则由 `kset-starter-gateway` 处理。
- Nacos 地址优先读取 `spring.cloud.nacos.config.server-addr`，缺省时回退到 discovery 地址。
- 自定义规则变更处理可实现 `CloudRuleProvider`。
