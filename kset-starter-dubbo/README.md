# KSet Dubbo Starter

`kset-starter-dubbo` 集成 Apache Dubbo、Nacos 注册与 KSet 标签路由。TraceId 透传与 RPC Transaction 由 `kset-starter-monitor` 的 Dubbo 插件提供。

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
