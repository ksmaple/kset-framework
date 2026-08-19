# kset-framework

KSet 公共框架 — 统一版本管理、按能力拆分的 Starter、云服务规则定制层。

业务怎么接入：先看 [docs/README.md](docs/README.md)，再按场景打开 `docs/usage/` 里的一篇。模块配置清单在各 Starter 的 `README.md`。

## 仓库

| 项 | 值 |
|----|-----|
| 目录名 | `kset-framework` |
| GitHub | [ksmaple/kset-framework](https://github.com/ksmaple/kset-framework) |
| 克隆 | `git clone git@github.com:ksmaple/kset-framework.git` |

> 自 `kset-comm` 更名为 `kset-framework`：根聚合 `artifactId`、BOM `kset-boot-parent`、Starter `kset-starter-*`；子模块 `kset-common` 名称不变。

当前构件版本以 `kset-boot-parent` 的 `<version>` 为准（现为 `1.0.12-SNAPSHOT`）。发布到仓库后去掉 `-SNAPSHOT`。

## 模块结构

```
kset-framework/
├── kset-boot-parent/     # 版本 BOM（Boot 3.5.14 / SC 2025.0.2 / SCA 2025.0.0.0 / Dubbo 3.3.6）
├── kset-common/          # 公共工具（异常、日志、监控门面 API、DateHelper、HTTP、线程池、Parallel、Retryer）
├── kset-cloud/           # 云服务规范（kset.cloud.*、SPI）
├── kset-starter-web/     # Web + 统一异常
├── kset-starter-auth/    # 登录态 + 多套鉴权 + 上下文透传
├── kset-starter-monitor/ # 全链路监控（TraceId/灰度/线程池 MDC，引入即生效）
├── kset-starter-datasource/  # JDBC + MyBatis-Plus + dynamic-datasource
├── kset-starter-cache/   # 多级缓存门面（L1 Caffeine，L2 SPI）
├── kset-starter-redis/   # Spring Data Redis (Lettuce)
├── kset-starter-nacos/   # Nacos 注册发现/配置 + 灰度 LoadBalancer
├── kset-starter-sentinel/# Sentinel 限流/熔断（规则从 Nacos 拉取）
├── kset-starter-dubbo/   # Dubbo RPC + 标签路由
├── kset-starter-gateway/ # Spring Cloud Gateway + 动态路由 + Sentinel
└── kset-starter-mq/      # RocketMQ 事件门面实现 + topic/tag 约定
```

## 包名与模块目录约定

Java 包根路径与 Maven 模块目录一一对应（`src/main/java` 下目录即包路径）：

| Maven 模块 | 包根路径 | 说明 |
|------------|----------|------|
| `kset-common` | `com.kset.common` | 公共工具、异常、日志、`com.kset.common.monitor` 监控门面 API |
| `kset-cloud` | `com.kset.cloud` | 云服务规范、SPI、共享配置与 Nacos 命名约定 |
| `kset-starter-web` | `com.kset.web` | Web 自动配置、`@OpLog`、`ApiResponse` |
| `kset-starter-auth` | `com.kset.auth` | 登录态、默认主体、多套鉴权、Gateway/Web/Dubbo 上下文透传 |
| `kset-starter-monitor` | `com.kset.common.monitor` | TraceId Filter、MDC 实现、Dubbo / Gateway / 线程池 |
| `kset-starter-datasource` | `com.kset.datasource` | JDBC、MyBatis-Plus、dynamic-datasource、自动填充 |
| `kset-starter-cache` | `com.kset.cache` | 缓存门面、L1 Caffeine、注解 AOP、L2 SPI |
| `kset-starter-redis` | `com.kset.redis` | Redis 模板、锁、排行榜 |
| `kset-starter-nacos` | `com.kset.nacos` | Nacos 发现/配置、灰度 LB |
| `kset-starter-sentinel` | `com.kset.sentinel` | Sentinel 规则与 SCA 集成 |
| `kset-starter-dubbo` | `com.kset.dubbo` | Dubbo 治理与路由 |
| `kset-starter-gateway` | `com.kset.gateway` | Gateway 过滤器与动态路由 |
| `kset-starter-mq` | `com.kset.mq` | RocketMQ 事件门面实现与 topic/tag 约定 |

跨模块依赖时，Starter 实现类引用 `kset-cloud` 中的共享 API（如 `com.kset.cloud.spi.CloudRuleProvider`、`com.kset.cloud.nacos.NacosConfigConvention`）。

## 文档

使用说明按场景放在 [docs/README.md](docs/README.md)。模块原理与配置项：

| 文档 | 说明 |
|------|------|
| [docs/README.md](docs/README.md) | 场景索引（并行、重试、锁、事件、鉴权、网关等） |
| [docs/design/resource-permission.md](docs/design/resource-permission.md) | 资源权限规范（功能码 / 资源 ACL 两平面；本仓暂无实现） |
| [kset-boot-parent/README.md](kset-boot-parent/README.md) | Java 21、Spring Boot / Cloud / Alibaba / Dubbo 版本基线与发布 |
| [kset-common/README.md](kset-common/README.md) | `ListHelper`、`DateHelper`、`KsetHttp`、线程池、随机、签名、`KsetContext` |
| [kset-cloud/README.md](kset-cloud/README.md) | 云服务公共配置、Nacos 命名约定、灰度与规则 SPI |
| [kset-starter-web/README.md](kset-starter-web/README.md) | Web、统一响应、异常、日志与 TraceId |
| [kset-starter-auth/README.md](kset-starter-auth/README.md) | 登录态、多套主体鉴权、Gateway/Web/Dubbo 上下文透传 |
| [kset-starter-monitor/README.md](kset-starter-monitor/README.md) | 全链路监控门面层、无感知矩阵与配置 |
| [kset-starter-datasource/README.md](kset-starter-datasource/README.md) | 数据源、MyBatis-Plus、dynamic-datasource |
| [kset-starter-cache/README.md](kset-starter-cache/README.md) | 多级缓存门面、注解、编程式 API、L1/L2、指标 |
| [kset-starter-redis/README.md](kset-starter-redis/README.md) | Redis 统一抽象、强制 TTL、分布式锁、排行榜、多数据源 |
| [kset-starter-nacos/README.md](kset-starter-nacos/README.md) | Nacos 注册发现/配置与公共配置导入 |
| [kset-starter-sentinel/README.md](kset-starter-sentinel/README.md) | Sentinel 规则从 Nacos 加载 |
| [kset-starter-dubbo/README.md](kset-starter-dubbo/README.md) | Dubbo RPC、Nacos 注册与灰度路由 |
| [kset-starter-gateway/README.md](kset-starter-gateway/README.md) | Gateway 动态路由、灰度、Sentinel 与鉴权 SPI |
| [kset-starter-mq/README.md](kset-starter-mq/README.md) | RocketMQ 事件门面实现与 topic/tag 约定 |

## Starter 能力说明

| Starter | KSet 定制能力 | 第三方默认行为 |
|---------|--------------|----------------|
| `kset-starter-web` | 全局异常、ApiResponse、OpLog AOP、请求日志、TraceId 响应 | Spring MVC / Validation |
| `kset-starter-auth` | 登录态、默认主体、多套鉴权规则、Gateway/Web/Dubbo 上下文透传、权限注解 | 可选 Redis session / Servlet / Gateway / Dubbo / AOP |
| `kset-starter-monitor` | Servlet TraceId/灰度、Dubbo/Gateway 透传、线程池 MDC 传播（默认开启） | 按 classpath 条件装配 |
| `kset-starter-datasource` | 逻辑删除约定、多种常见创建/更新时间字段自动填充、dynamic-datasource 单库默认关闭 | JDBC / MyBatis-Plus / dynamic-datasource |
| `kset-starter-cache` | KSet 自定义缓存注解、多级缓存、L1 Caffeine、L2 SPI、本地 single-flight | Caffeine / Spring AOP |
| `kset-starter-redis` | JSON 序列化 RedisTemplate、Key 前缀、分布式锁、排行榜、可选 Cache L2 | Spring Data Redis / Redisson |
| `kset-starter-nacos` | Nacos 命名约定、灰度 LB（**不含** Web / Sentinel） | SCA Nacos |
| `kset-starter-sentinel` | 限流/熔断/热点规则从 Nacos 加载 | SCA Sentinel |
| `kset-starter-dubbo` | 标签路由、路由冷启动拉取（**不依赖** nacos starter；Trace 见 monitor） | Apache Dubbo + Nacos Config |
| `kset-starter-gateway` | 动态路由 diff、灰度、可选鉴权、Gateway Sentinel（Trace 见 monitor） | Spring Cloud Gateway |
| `kset-starter-mq` | RocketMQ 组件依赖入口；事件门面默认在 `kset-common` 提供 Spring 本地实现 | RocketMQ V5 Client Spring Boot Starter |

## 组件接入总览

| 组件 | Maven 依赖 | 主要入口 | 最小配置/说明 |
|------|------------|----------|---------------|
| 版本基线 | `kset-boot-parent` | Maven parent / BOM | 统一 Java 21、UTF-8、Boot/Cloud/Alibaba/Dubbo 等版本 |
| 公共工具 | `kset-common` | `ListHelper`、`DateHelper`、`KsetHttp`、`Parallel`、`Retryer`、`KsetContext` | 也会由任意 starter 传递引入 |
| 事件门面 | `kset-common` / `kset-starter-mq` | `EventFacade`、`EventHandler`、`SendCallback` | 默认 Spring 本地事件；引入 MQ 后自动切换 RocketMQ |
| Web | `kset-starter-web` | `ApiResponse`、`@OpLog`、Controller | 提供 Web 基础能力与统一响应 |
| Auth | `kset-starter-auth` | `LoginContext`、`@RequireLogin`、`@RequirePermission`、`LoginSessionStore` | 默认 `app + session + X-Session-Token`；`web.mode` 默认 `redis` |
| 监控 | `kset-starter-monitor` | `Monitor`、`@Monitored`、Trace Filter | 默认 log backend；CAT 需显式配置 |
| 数据源公共能力 | `kset-starter-datasource` | MyBatis-Plus Mapper / Entity | 配置 `spring.datasource.*`、`kset.datasource.auto-fill` |
| 多级缓存 | `kset-starter-cache` | `@KsetCacheable`、`KsetCache`、`KsetCacheFacade` | L1 Caffeine；L2 通过 SPI 接入 |
| Redis L2 适配 | `kset-starter-redis` + `kset-starter-cache` | `RedisKsetCacheStore` 自动注册 | 引入 Redis 后为 cache 组件提供 L2 |
| Redis 操作 | `kset-starter-redis` | `KsetRedisService`、`KsetRedis`、`KsetRedisKeys` | 配置 `spring.data.redis.*`、`kset.redis.default-ttl` |
| Redis 锁 | `kset-starter-redis` | `KsetRedisLockExecutor`、`@KsetLocked` | Redisson 默认使用主 Redis，可用 `kset.redis.redisson.enabled=false` 关闭 |
| 排行榜 | `kset-starter-redis` | `KsetRedisRankService` | 基于 Redis ZSET，榜单参数代码指定 |
| Nacos | `kset-starter-nacos` | SCA Nacos / `NacosConfigConvention` | 配置 `spring.cloud.nacos.*` |
| Sentinel | `kset-starter-sentinel` | Nacos 规则加载 | 配置 `kset.cloud.sentinel.*` |
| Dubbo | `kset-starter-dubbo` | Dubbo Provider/Consumer、路由治理 | 配置 `dubbo.registry.address` |
| Gateway | `kset-starter-gateway` | Gateway 路由、灰度、鉴权 SPI | 独立进程，勿与 `starter-web` 同用 |
| MQ | `kset-starter-mq` | RocketMQ V5 Client Spring Boot Starter | 自动提供 RocketMQ 版 `EventFacade` |
| Cloud SPI | `kset-cloud` | `CloudRuleProvider`、`GrayTagResolver` | starter 内部共享，也可业务扩展 |

## 版本矩阵

| 组件 | 版本 |
|------|------|
| Spring Boot | 3.5.14 |
| Java | 21 |
| Spring Cloud | 2025.0.2 |
| Spring Cloud Alibaba | 2025.0.0.0 |
| Apache Dubbo | 3.3.6 |
| MyBatis-Plus | 3.5.5 |
| dynamic-datasource | 4.5.0 |
| PostgreSQL JDBC | 42.7.5 |
| SQLite JDBC | 3.45.2.0 |
| MySQL Connector/J | 8.3.0 |
| Jedis | 5.1.2 |
| Redisson | 3.40.2 |
| Guava | 33.4.0-jre |
| Commons Lang3 | 3.17.0 |
| Commons Collections4 | 4.4 |
| Commons IO | 2.18.0 |
| Commons Codec | 1.17.1 |
| TransmittableThreadLocal | 2.14.5 |
| RocketMQ V5 Spring Starter | 2.3.5 |
| EasyExcel | 4.0.3 |
| Caffeine | 3.2.0 |
| JJWT | 0.12.6 |
| Apache POI | 5.3.0 |
| Apache Tika | 2.9.1 |
| BouncyCastle (jdk18on) | 1.78.1 |
| Protobuf Java | 3.25.8 |
| Commons Compress | 1.26.2 |

完整 `dependencyManagement` 以 `kset-boot-parent/pom.xml` 为准。Spring Boot 3.5.x 对齐 Spring Cloud 2025.0.x，Spring Cloud Alibaba 使用 2025.0.0.0 版本线。

### 依赖分层

| 层级 | 模块 | 说明 |
|------|------|------|
| BOM | `kset-boot-parent` | 锁定全量三方版本 |
| 工具聚合 | `kset-common` | Commons / Guava / Jackson / TTL 等**仅在此声明** |
| 能力 | `kset-cloud`、`kset-starter-*` | **必须**依赖 `kset-common`；只声明领域能力，勿重复工具库；`starter-nacos` / `starter-sentinel` / `starter-web` **解耦**，微服务按需组合 |

业务项目引入任意 KSet Starter 后，上述工具库会随 `kset-common` **传递**进入 classpath，一般无需再单独声明 `commons-lang3`、`guava` 等。

若未使用 KSet Starter、仅继承 `kset-boot-parent`，可按需从 BOM 引用（**无需写 version**）：

```xml
<dependency>
    <groupId>com.kset</groupId>
    <artifactId>kset-common</artifactId>
</dependency>
```

**依赖冲突排查**（全 reactor 扫描 `omitted for conflict`）：

```bash
mvn dependency:tree -Dverbose | findstr "omitted for conflict"
```

当前 BOM 已统一 `protobuf-java`（MySQL Connector 3.25.1 vs Dubbo 3.25.8）、POI 全家桶与 BouncyCastle 传递版本。

| 项 | 要求 | 说明 |
|----|------|------|
| **JDK** | **21**（LTS） | `maven-enforcer-plugin` 构建时校验 `[21,22)`；IDE Project SDK 选 21 |
| **Maven** | **3.9+** | 构建时校验；避免使用会解析 `maven-compiler-plugin` 4.x beta 的异常环境 |
| **源码编码** | **UTF-8** | `project.build.sourceEncoding` / `.editorconfig` / `.gitattributes` 已统一 |
| **编译** | `--release 21` | 由 `kset-boot-parent` 配置，含 `-parameters`（Spring / Dubbo 需要） |
| **JVM 默认编码** | UTF-8 | 仓库 `.mvn/jvm.config` 已设置 `-Dfile.encoding=UTF-8` |

**IDE 建议（IntelliJ / Cursor）：**

1. **Project Structure → Project SDK**：JDK **21**
2. **Settings → Editor → File Encodings**：Global / Project 均为 **UTF-8**
3. **Maven → Reload All Maven Projects**（修改 parent POM 后必做）
4. 终端若中文乱码，确认 `JAVA_TOOL_OPTIONS` 或系统区域设置未强制 GBK

**业务项目继承 parent 时无需再配 Java 版本与编码**，除非有特殊模块需求。

## 快速开始

本节给出单机 / 微服务 Cloud 的最小依赖组合；完整配置与代码见 [docs/README.md](docs/README.md)。

```xml
<parent>
    <groupId>com.kset</groupId>
    <artifactId>kset-boot-parent</artifactId>
    <version>1.0.12-SNAPSHOT</version>
</parent>
```

**单机** — 仅业务进程，无 Nacos/Dubbo/Gateway：

```xml
<dependency>
    <groupId>com.kset</groupId>
    <artifactId>kset-starter-web</artifactId>
</dependency>
<dependency>
    <groupId>com.kset</groupId>
    <artifactId>kset-starter-auth</artifactId>
</dependency>
<dependency>
    <groupId>com.kset</groupId>
    <artifactId>kset-starter-datasource</artifactId>
</dependency>
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
</dependency>
<dependency>
    <groupId>com.kset</groupId>
    <artifactId>kset-starter-cache</artifactId>
</dependency>
<dependency>
    <groupId>com.kset</groupId>
    <artifactId>kset-starter-redis</artifactId>
</dependency>
<dependency>
    <groupId>com.kset</groupId>
    <artifactId>kset-starter-monitor</artifactId>
</dependency>
```

本地无 MySQL 时，可将驱动换成 `sqlite-jdbc` 并把 `spring.datasource.url` 指到文件库。生产按 MySQL 8 接入。

**微服务 Cloud** — 业务服务在此基础上增加：

```xml
<dependency>
    <groupId>com.kset</groupId>
    <artifactId>kset-starter-nacos</artifactId>
</dependency>
<dependency>
    <groupId>com.kset</groupId>
    <artifactId>kset-starter-sentinel</artifactId>
</dependency>
<dependency>
    <groupId>com.kset</groupId>
    <artifactId>kset-starter-dubbo</artifactId>
</dependency>
```

**Gateway**（独立进程，勿与 starter-web 同用）：

```xml
<dependency>
    <groupId>com.kset</groupId>
    <artifactId>kset-starter-gateway</artifactId>
</dependency>
<dependency>
    <groupId>com.kset</groupId>
    <artifactId>kset-starter-auth</artifactId>
</dependency>
<dependency>
    <groupId>com.kset</groupId>
    <artifactId>kset-starter-monitor</artifactId>
</dependency>
```

## Nacos 规则配置约定

| 用途 | dataId 格式 | 示例 |
|------|------------|------|
| 应用主配置 | `{app}.yaml` | `order-service.yaml` |
| 公共配置 | `kset-common.yaml` | 团队共享默认值 |
| Sentinel 限流 | `{app}-flow-rules` | JSON 数组 |
| Sentinel 熔断 | `{app}-degrade-rules` | JSON 数组 |
| Sentinel 热点 | `{app}-param-flow-rules` | JSON 数组 |
| Dubbo 路由 | `{app}-route-rules` | JSON 对象 |
| Gateway 路由 | `{gateway-app}-gateway-routes` | JSON 数组 |
| Gateway 限流 | `{gateway-app}-gateway-flow-rules` | JSON 数组 |

JSON 样例见 [docs/usage/cloud.md](docs/usage/cloud.md) 与对应模块 README。

## SPI 扩展

实现接口并注册为 Spring `@Component`：

| SPI | 包路径 | 用途 |
|-----|--------|------|
| `CloudRuleProvider` | `com.kset.cloud.spi` | 自定义 Sentinel / Dubbo / Gateway 规则变更处理 |
| `GrayTagResolver` | `com.kset.cloud.spi` | 自定义灰度标签解析（默认透传 Header） |
| `GatewayAuthProvider` | `com.kset.gateway.spi` | Gateway JWT / Token 鉴权；`Mono.empty()` 放行，不要「头非空就过」 |

## 全链路灰度

```
Client → Gateway (X-Gray-Tag) → LoadBalancer (metadata 匹配) → 微服务 (starter-monitor) → Dubbo (starter-monitor)
```

## 构建

```bash
mvn clean install
```
