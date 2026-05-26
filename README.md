# kset-comm

KSet Spring Boot 公共框架 — 统一版本管理、按能力拆分的 Starter、云服务规则定制层。

## 模块结构

```
kset-comm/
├── kset-spring-boot-parent/              # 版本 BOM（Boot 3.4.5 / SC 2024 / SCA 2023 / Dubbo 3.3）
├── kset-common/                          # 公共工具（异常、日志、TraceId、加密）
├── kset-cloud/                           # 云服务规范（kset.cloud.*、SPI、TraceContext）
├── kset-spring-boot-starter-web/         # Web + 统一异常 + TraceId
├── kset-spring-boot-starter-mysql/       # JDBC + MySQL + MyBatis-Plus + Flyway
├── kset-spring-boot-starter-redis/       # Spring Data Redis (Lettuce)
├── kset-spring-boot-starter-nacos/       # Nacos + Sentinel + 灰度 LoadBalancer
├── kset-spring-boot-starter-dubbo/       # Dubbo RPC + TraceId 透传 + 标签路由
└── kset-spring-boot-starter-gateway/     # Spring Cloud Gateway + 动态路由 + Sentinel
└── kset-demo/                            # 示例：standalone（单机）+ cloud（微服务）
```

## 包名与模块目录约定

Java 包根路径与 Maven 模块目录一一对应（`src/main/java` 下目录即包路径）：

| Maven 模块 | 包根路径 | 说明 |
|------------|----------|------|
| `kset-common` | `com.kset.common` | 公共工具、异常、日志、TraceId |
| `kset-cloud` | `com.kset.cloud` | 云服务规范、SPI、共享配置与 Nacos 命名约定 |
| `kset-spring-boot-starter-web` | `com.kset.web` | Web 自动配置与统一响应 |
| `kset-spring-boot-starter-mysql` | `com.kset.mysql` | MyBatis-Plus / Flyway |
| `kset-spring-boot-starter-redis` | `com.kset.redis` | Redis 模板与缓存 |
| `kset-spring-boot-starter-nacos` | `com.kset.nacos` | Nacos / Sentinel 自动配置 |
| `kset-spring-boot-starter-dubbo` | `com.kset.dubbo` | Dubbo 治理与路由 |
| `kset-spring-boot-starter-gateway` | `com.kset.gateway` | Gateway 过滤器与动态路由 |
| `kset-demo/*` | `com.kset.demo.*` | 示例应用 |

跨模块依赖时，Starter 实现类引用 `kset-cloud` 中的共享 API（如 `com.kset.cloud.spi.CloudRuleProvider`、`com.kset.cloud.nacos.NacosConfigConvention`）。

## 文档

| 文档 | 说明 |
|------|------|
| [docs/getting-started.md](docs/getting-started.md) | **单机 vs 微服务 Cloud** 依赖与配置 |
| [kset-demo/README.md](kset-demo/README.md) | 两个示例工程启动说明 |
| [docs/openapi.md](docs/openapi.md) | API 文档（Knife4j / OpenAPI 3） |
| [docs/nacos/demo-gateway-routes.json](docs/nacos/demo-gateway-routes.json) | Gateway 路由样例 |

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

完整说明见 **[docs/getting-started.md](docs/getting-started.md)**（单机 / 微服务 Cloud 分开展示）。

| 场景 | 示例工程 | Starter 组合 |
|------|----------|--------------|
| **单机** | `kset-demo/demo-standalone-service` | web + mysql + redis |
| **微服务 Cloud** | `demo-user-service` / `demo-order-service` / `demo-gateway` | web + mysql (+redis) + nacos + dubbo；网关单独 gateway |

```xml
<parent>
    <groupId>com.kset</groupId>
    <artifactId>kset-spring-boot-parent</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</parent>
```

**单机** — 仅业务进程，无 Nacos/Dubbo/Gateway：

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

**微服务 Cloud** — 业务服务在此基础上增加：

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

**Gateway**（独立进程，勿与 starter-web 同用）：

```xml
<dependency>
    <groupId>com.kset</groupId>
    <artifactId>kset-spring-boot-starter-gateway</artifactId>
</dependency>
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
| `GatewayAuthProvider` | `com.kset.gateway.spi` | Gateway JWT / Token 鉴权 |

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

详见 [kset-demo/README.md](kset-demo/README.md)。

**单机**（MySQL + Redis）：

```bash
mvn -pl kset-demo/demo-standalone-service spring-boot:run
# http://localhost:8080/doc.html
```

**微服务 Cloud**（MySQL + Redis + Nacos）：

```bash
mvn -pl kset-demo/demo-user-service spring-boot:run
mvn -pl kset-demo/demo-order-service spring-boot:run
mvn -pl kset-demo/demo-gateway spring-boot:run
```

Gateway 路由：Nacos 配置 `demo-gateway-gateway-routes` 为 [docs/nacos/demo-gateway-routes.json](docs/nacos/demo-gateway-routes.json)。

## 构建

```bash
mvn clean install
```

## 后续迭代

- 完整 JWT/OAuth2 Gateway 鉴权
- Dubbo starter 依赖瘦身（纯 RPC 场景）
