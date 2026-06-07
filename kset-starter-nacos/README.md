# KSet Nacos Starter

`kset-starter-nacos` 集成 Spring Cloud Alibaba Nacos 注册发现、配置中心、KSet 命名约定和灰度 LoadBalancer。它不传递 `kset-starter-web` / `kset-starter-sentinel`，业务按场景显式组合。

## 依赖

```xml
<dependency>
    <groupId>com.kset</groupId>
    <artifactId>kset-starter-nacos</artifactId>
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
        namespace: dev
        group: KSET_GROUP
      config:
        server-addr: ${NACOS_ADDR:127.0.0.1:8848}
        namespace: dev
        group: KSET_GROUP
  config:
    import: optional:nacos:${spring.application.name}.yaml
```

引入 starter 后，框架会按需补齐 Nacos group / namespace 默认值，并追加公共配置 `optional:nacos:kset-common.yaml`。

## 规则监听

`kset-starter-nacos` 会注册 `CloudRuleProvider` 监听器，按 `NacosConfigConvention` 监听 Dubbo、Gateway 等共享规则 dataId。具体规则加载和落地仍由对应 starter 负责。

## 边界

- Nacos 只负责注册发现、配置、公共命名约定和灰度 LB。
- Sentinel 规则加载使用 `kset-starter-sentinel`。
- Gateway 动态路由使用 `kset-starter-gateway`。
- Dubbo 治理使用 `kset-starter-dubbo`。
