# kset-boot

KSet Spring Boot 公共框架 — 统一版本管理、按能力拆分的 Starter、云服务规则定制层。

## 模块结构

```
kset-boot/
├── kset-spring-boot-parent/              # 版本 BOM（Boot 3.4.5 / SC 2024 / SCA 2023 / Dubbo 3.3）
├── kset-common/                          # 公共工具（异常、日志、TraceId、加密）
├── kset-cloud/                           # 云服务规范（kset.cloud.*、SPI、TraceContext）
├── kset-spring-boot-starter-web/         # Web + 统一异常 + TraceId
├── kset-spring-boot-starter-mysql/       # JDBC + MySQL + MyBatis-Plus + Flyway
├── kset-spring-boot-starter-redis/       # Spring Data Redis (Lettuce)
├── kset-spring-boot-starter-nacos/       # Nacos + Sentinel + 灰度 LoadBalancer
├── kset-spring-boot-starter-dubbo/       # Dubbo RPC + TraceId 透传 + 标签路由
└── kset-spring-boot-starter-gateway/     # Spring Cloud Gateway + 动态路由 + Sentinel
└── kset-demo/                            # 示例工程（user / order / gateway）
```

## Starter 能力说明

| Starter | KSet 定制能力 | 第三方默认行为 |
|---------|--------------|----------------|
| `kset-spring-boot-starter-web` | TraceId、全局异常、ApiResponse、OpLog AOP、可选 Knife4j/请求日志 | Spring MVC / Validation |
| `kset-spring-boot-starter-mysql` | 逻辑删除约定、createTime/updateTime 自动填充、Flyway 默认路径 | JDBC / MyBatis-Plus / Flyway |
| `kset-spring-boot-starter-redis` | JSON 序列化 RedisTemplate、Key 前缀、可选 Cache | Spring Data Redis |
| `kset-spring-boot-starter-nacos` | Nacos 命名约定、Sentinel 三类规则、灰度 LB | SCA Nacos / Sentinel |
| `kset-spring-boot-starter-dubbo` | Trace/灰度透传、标签路由、路由冷启动拉取 | Apache Dubbo |
| `kset-spring-boot-starter-gateway` | 动态路由 diff、TraceId/灰度、可选鉴权、Gateway Sentinel | Spring Cloud Gateway |

## 版本矩阵

| 组件 | 版本 |
|------|------|
| Spring Boot | 3.4.5 |
| Java | 21 |
| Spring Cloud | 2024.0.0 |
| Spring Cloud Alibaba | 2023.0.3.2 |
| Apache Dubbo | 3.3.6 |
| MyBatis-Plus | 3.5.5 |

## 快速开始

业务项目继承 parent：

```xml
<parent>
    <groupId>com.kset</groupId>
    <artifactId>kset-spring-boot-parent</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</parent>
```

### 单体服务

```xml
<dependency>
    <groupId>com.kset</groupId>
    <artifactId>kset-spring-boot-starter-web</artifactId>
</dependency>
<dependency>
    <groupId>com.kset</groupId>
    <artifactId>kset-spring-boot-starter-mysql</artifactId>
</dependency>
<dependency>
    <groupId>com.kset</groupId>
    <artifactId>kset-spring-boot-starter-redis</artifactId>
</dependency>
```

### 微服务

```xml
<dependency>
    <groupId>com.kset</groupId>
    <artifactId>kset-spring-boot-starter-nacos</artifactId>
</dependency>
<dependency>
    <groupId>com.kset</groupId>
    <artifactId>kset-spring-boot-starter-dubbo</artifactId>
</dependency>
```

### API Gateway（勿与 starter-web 同时使用）

```xml
<dependency>
    <groupId>com.kset</groupId>
    <artifactId>kset-spring-boot-starter-gateway</artifactId>
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
  config:
    import: optional:nacos:${spring.application.name}.yaml
  datasource:
    url: jdbc:mysql://localhost:3306/demo
    username: root
    password: root
  data:
    redis:
      host: localhost
      port: 6379

kset:
  web:
    oplog:
      enabled: true
    knife4j:
      enabled: true
    request-logging:
      enabled: false
  mysql:
    enabled: true
    auto-fill: true
    flyway:
      enabled: true
  redis:
    key-prefix: "myapp:"
    cache:
      enabled: false
  cloud:
    nacos:
      namespace: dev
      group: KSET_GROUP
    sentinel:
      enabled: true
    dubbo:
      trace-propagation-enabled: true
      gray-metadata-key: version
      default-gray-tag: stable
    loadbalancer:
      gray-header: X-Gray-Tag
      metadata-key: version

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

## Nacos 规则配置约定

| 用途 | dataId 格式 | 示例 |
|------|------------|------|
| 应用主配置 | `{app}.yaml` | `order-service.yaml` |
| 公共配置 | `kset-common.yaml` | 团队共享默认值 |
| Sentinel 限流 | `{app}-flow-rules` | JSON 数组 |
| Sentinel 熔断 | `{app}-degrade-rules` | JSON 数组 |
| Dubbo 路由 | `{app}-route-rules` | JSON 对象 |
| Gateway 路由 | `{gateway-app}-gateway-routes` | JSON 数组 |
| Gateway 限流 | `{gateway-app}-gateway-flow-rules` | JSON 数组 |

### Sentinel 限流示例

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

### Dubbo 路由示例

```json
{
  "conditions": [
    { "tag": "v2", "weight": 10 },
    { "tag": "stable", "weight": 90 }
  ]
}
```

### Gateway 动态路由示例

```json
[
  {
    "id": "order-service",
    "uri": "lb://order-service",
    "predicates": [
      { "name": "Path", "args": { "pattern": "/api/orders/**" } }
    ],
    "filters": [
      { "name": "StripPrefix", "args": { "parts": "1" } }
    ]
  }
]
```

## SPI 扩展

实现接口并注册为 Spring `@Component`：

| SPI | 包路径 | 用途 |
|-----|--------|------|
| `CloudRuleProvider` | `com.kset.cloud.spi` | 自定义 Sentinel / Dubbo / Gateway 规则变更处理 |
| `GrayTagResolver` | `com.kset.cloud.spi` | 自定义灰度标签解析（默认透传 Header） |
| `GatewayAuthProvider` | `com.kset.cloud.gateway.spi` | Gateway JWT / Token 鉴权 |

```java
import com.kset.cloud.spi.CloudRuleProvider;
import com.kset.cloud.spi.CloudRuleType;

@Component
public class OrderFlowRuleProvider implements CloudRuleProvider {
    @Override
    public CloudRuleType ruleType() {
        return CloudRuleType.SENTINEL_FLOW;
    }

    @Override
    public void onRuleChanged(String jsonContent) {
        // 额外处理逻辑
    }
}
```

## 全链路灰度

```
Client → Gateway (X-Gray-Tag) → LoadBalancer (metadata 匹配) → 微服务 (TraceIdFilter) → Dubbo (TraceFilter)
```

## kset-demo 本地启动

前置：MySQL（库 `kset_demo`）、Redis、Nacos（可选，配置 `NACOS_ADDR`）。

```bash
mvn clean install
# 终端 1
mvn -pl kset-demo/demo-user-service spring-boot:run
# 终端 2
mvn -pl kset-demo/demo-order-service spring-boot:run
# 终端 3
mvn -pl kset-demo/demo-gateway spring-boot:run
```

- 用户服务：`http://localhost:8081/api/users/1`
- 订单服务：`http://localhost:8082/api/orders/1`（Dubbo 查用户名 + Redis 缓存）
- 网关：将 Nacos 中 `demo-gateway-gateway-routes` 配置为 [docs/nacos/demo-gateway-routes.json](docs/nacos/demo-gateway-routes.json) 内容

## 构建

```bash
mvn clean install
```

## 后续迭代

- 完整 JWT/OAuth2 Gateway 鉴权
- Dubbo starter 依赖瘦身（纯 RPC 场景）
