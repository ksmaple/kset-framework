# kset-framework

KSet 公共框架 — 统一版本管理、按能力拆分的 Starter、云服务规则定制层。

## 仓库

| 项 | 值 |
|----|-----|
| 目录名 | `kset-framework` |
| GitHub | [ksmaple/kset-framework](https://github.com/ksmaple/kset-framework) |
| 克隆 | `git clone git@github.com:ksmaple/kset-framework.git` |

> 自 `kset-comm` 更名为 `kset-framework`：根聚合 `artifactId`、BOM `kset-boot-parent`、Starter `kset-starter-*`；子模块 `kset-common` 名称不变。

## 模块结构

```
kset-framework/
├── kset-boot-parent/              # 版本 BOM（Boot 3.5.14 / SC 2025.0.2 / SCA 2025.0.0.0 / Dubbo 3.3.6）
├── kset-common/                          # 公共工具（异常、日志、监控门面 API、DateHelper、HTTP、线程池）
├── kset-cloud/                           # 云服务规范（kset.cloud.*、SPI）
├── kset-starter-web/         # Web + 统一异常
├── kset-starter-auth/        # 登录态 + 多套鉴权 + 上下文透传
├── kset-starter-monitor/     # 全链路监控（TraceId/灰度/线程池 MDC，引入即生效）
├── kset-starter-datasource/  # JDBC + MyBatis-Plus + dynamic-datasource 公共数据源能力
├── kset-starter-cache/       # 多级缓存门面（L1 Caffeine，L2 SPI）
├── kset-starter-redis/       # Spring Data Redis (Lettuce)
├── kset-starter-nacos/       # Nacos 注册发现/配置 + 灰度 LoadBalancer
├── kset-starter-sentinel/    # Sentinel 限流/熔断（规则从 Nacos 拉取）
├── kset-starter-dubbo/       # Dubbo RPC + TraceId 透传 + 标签路由
├── kset-starter-gateway/     # Spring Cloud Gateway + 动态路由 + Sentinel
└── kset-starter-mq/          # RocketMQ 事件门面实现 + topic/tag 约定
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
| `kset-starter-redis` | `com.kset.redis` | Redis 模板与缓存 |
| `kset-starter-nacos` | `com.kset.nacos` | Nacos 发现/配置、灰度 LB |
| `kset-starter-sentinel` | `com.kset.sentinel` | Sentinel 规则与 SCA 集成 |
| `kset-starter-dubbo` | `com.kset.dubbo` | Dubbo 治理与路由 |
| `kset-starter-gateway` | `com.kset.gateway` | Gateway 过滤器与动态路由 |
| `kset-starter-mq` | `com.kset.mq` | RocketMQ 事件门面实现与 topic/tag 约定 |

跨模块依赖时，Starter 实现类引用 `kset-cloud` 中的共享 API（如 `com.kset.cloud.spi.CloudRuleProvider`、`com.kset.cloud.nacos.NacosConfigConvention`）。

## 文档

| 文档 | 说明 |
|------|------|
| [kset-boot-parent/README.md](kset-boot-parent/README.md) | Java 21、Spring Boot / Cloud / Alibaba / Dubbo 版本基线与发布 |
| [kset-common/README.md](kset-common/README.md) | `ListHelper`、`DateHelper`（java.time）、`KsetHttp`、线程池、随机、签名 |
| [kset-cloud/README.md](kset-cloud/README.md) | 云服务公共配置、Nacos 命名约定、灰度与规则 SPI |
| [kset-starter-web/README.md](kset-starter-web/README.md) | API 文档（Knife4j / OpenAPI 3） |
| [kset-starter-auth/README.md](kset-starter-auth/README.md) | 登录态、项目默认鉴权、多套主体鉴权、Gateway/Web/Dubbo 上下文透传 |
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
| `kset-starter-web` | 全局异常、ApiResponse、OpLog AOP、可选 Knife4j/请求日志 | Spring MVC / Validation |
| `kset-starter-auth` | 登录态、默认主体、多套鉴权规则、Gateway/Web/Dubbo 上下文透传、权限注解 | 可选 Redis session / Servlet / Gateway / Dubbo / AOP |
| `kset-starter-monitor` | Servlet TraceId/灰度、Dubbo/Gateway 透传、线程池 MDC 传播（默认开启） | 按 classpath 条件装配 |
| `kset-starter-datasource` | 逻辑删除约定、createTime/updateTime 自动填充、dynamic-datasource 单库默认关闭 | JDBC / MyBatis-Plus / dynamic-datasource |
| `kset-starter-cache` | KSet 自定义缓存注解、多级缓存、L1 Caffeine、L2 SPI、本地 single-flight | Caffeine / Spring AOP |
| `kset-starter-redis` | JSON 序列化 RedisTemplate、Key 前缀、可选 Cache | Spring Data Redis |
| `kset-starter-nacos` | Nacos 命名约定、灰度 LB（**不含** Web / Sentinel） | SCA Nacos |
| `kset-starter-sentinel` | 限流/熔断/热点规则从 Nacos 加载 | SCA Sentinel |
| `kset-starter-dubbo` | 标签路由、路由冷启动拉取（**不依赖** nacos starter；Trace 见 monitor） | Apache Dubbo + Nacos Config |
| `kset-starter-gateway` | 动态路由 diff、灰度、可选鉴权、Gateway Sentinel（Trace 见 monitor） | Spring Cloud Gateway |
| `kset-starter-mq` | RocketMQ 组件依赖入口；事件门面默认在 `kset-common` 提供 Spring 本地实现 | RocketMQ V5 Client Spring Boot Starter |

## 组件接入总览

| 组件 | Maven 依赖 | 主要入口 | 最小配置/说明 |
|------|------------|----------|---------------|
| 版本基线 | `kset-boot-parent` | Maven parent / BOM | 统一 Java 21、UTF-8、Boot/Cloud/Alibaba/Dubbo 等版本 |
| 公共工具 | `kset-common` | `ListHelper`、`DateHelper`、`KsetHttp`、`KsetThreadPoolFactory`、`StructLog` | 也会由任意 starter 传递引入 |
| 事件门面 | `kset-common` / `kset-starter-mq` | `EventFacade`、`EventHandler`、`SendCallback` | 默认 Spring 本地事件；引入 MQ 后自动切换 RocketMQ |
| Web | `kset-starter-web` | `ApiResponse`、`@OpLog`、Controller | `knife4j.enable=true` 开启文档 |
| Auth | `kset-starter-auth` | `LoginContext`、`@RequireLogin`、`@RequirePermission`、`LoginSessionStore` | 默认 `app + session + X-Session-Token`，CMS 等差异化场景用 `kset.auth.rules` |
| OpenAPI | `kset-starter-web` | `/doc.html`、`/v3/api-docs`、`@Operation` | 生产建议关闭或走网关鉴权 |
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
| MQ | `kset-starter-mq` | RocketMQ V5 Client Spring Boot Starter | 自动提供 RocketMQ 版 `EventFacade`；业务事件统一走 `EventFacade` / `EventHandler` |
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
| Spring AI | 1.0.0 |
| PostgreSQL JDBC | 42.7.5 |
| SQLite JDBC | 3.45.2.0 |
| MySQL Connector/J | 8.3.0 |
| Jedis | 5.1.2 |
| Redisson | 3.40.2 |
| Fastjson2 | 2.0.53 |
| Guava | 33.4.0-jre |
| Commons Lang3 | 3.17.0 |
| Commons Collections4 | 4.4 |
| Commons IO | 2.18.0 |
| Commons Codec | 1.17.1 |
| TransmittableThreadLocal | 2.14.5 |
| RocketMQ V5 Spring Starter | 2.3.5 |
| EasyExcel | 4.0.3 |
| OkHttp | 4.12.0 |
| Caffeine | 3.2.0 |
| JJWT | 0.12.6 |
| Apache POI | 5.3.0 |
| Apache Tika | 2.9.1 |
| BouncyCastle (jdk18on) | 1.78.1 |
| Protobuf Java | 3.25.8 |
| Commons Compress | 1.26.2 |

Spring 生态版本以 `kset-boot-parent/pom.xml` 为准：Spring Boot 3.5.x 对齐 Spring Cloud 2025.0.x，Spring Cloud Alibaba 使用 2025.0.0.0 版本线。

### 依赖分层

| 层级 | 模块 | 说明 |
|------|------|------|
| BOM | `kset-boot-parent` | 锁定全量三方版本 |
| 工具聚合 | `kset-common` | Commons / Guava / OkHttp / Jackson / Fastjson2 / TTL 等**仅在此声明** |
| 能力 | `kset-cloud`、`kset-starter-*` | **必须**依赖 `kset-common`；只声明领域能力，勿重复工具库；`starter-nacos` / `starter-sentinel` / `starter-web` **解耦**，微服务按需组合 |

业务项目引入任意 KSet Starter 后，上述工具库会随 `kset-common` **传递**进入 classpath，一般无需再单独声明 `commons-lang3`、`guava` 等。

若未使用 KSet Starter、仅继承 `kset-boot-parent`，可按需从 BOM 引用（**无需写 version**）：

```xml
<!-- 与 kset-common 对齐的基础工具（推荐直接依赖 kset-common 而非逐项声明） -->
<dependency>
    <groupId>com.kset</groupId>
    <artifactId>kset-common</artifactId>
</dependency>

<!-- 可选扩展：缓存 / Excel / JWT / 加密 / 文档（版本由 BOM 管理） -->
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
</dependency>
<dependency>
    <groupId>com.alibaba</groupId>
    <artifactId>easyexcel</artifactId>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
</dependency>
<dependency>
    <groupId>org.bouncycastle</groupId>
    <artifactId>bcprov-jdk18on</artifactId>
</dependency>
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
</dependency>
```

更多版本见 `kset-boot-parent/pom.xml` 中 `dependencyManagement`。

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

本节给出单机 / 微服务 Cloud 的最小依赖组合；各组件完整配置见上方模块 README。

```xml
<parent>
    <groupId>com.kset</groupId>
    <artifactId>kset-boot-parent</artifactId>
    <version>1.0.8-SNAPSHOT</version>
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
    <artifactId>kset-starter-datasource</artifactId>
</dependency>
<dependency>
    <groupId>org.xerial</groupId>
    <artifactId>sqlite-jdbc</artifactId>
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
    <artifactId>kset-starter-monitor</artifactId>
</dependency>
```

## 组件用法速查

本节汇总所有框架组件的最小接入方式；完整细节见上方各模块 `README.md`。

### kset-boot-parent：版本与构建基线

业务工程继承 `kset-boot-parent` 后，Java 21、UTF-8、Spring Boot / Spring Cloud / Alibaba / Dubbo / MyBatis-Plus / RocketMQ 等版本均由 BOM 管理，子模块依赖无需再写 `version`。KSet 自身模块版本由 `kset-framework.version` 固定管理，不随业务工程的 `project.version` 变化。

```xml
<parent>
    <groupId>com.kset</groupId>
    <artifactId>kset-boot-parent</artifactId>
    <version>1.0.8-SNAPSHOT</version>
</parent>
```

### kset-common：公共工具、日志、监控与事件门面

任意 `kset-starter-*` 会传递依赖 `kset-common`；仅使用公共能力时可直接依赖：

```xml
<dependency>
    <groupId>com.kset</groupId>
    <artifactId>kset-common</artifactId>
</dependency>
```

常用工具入口：

```java
List<User> users = ListHelper.batchMap(ids, 500, userRepo::findByIds);
DateHelper day = DateHelper.parse("2024-06-07").withTime("15:30:00");
String body = KsetHttp.get("https://example.com/api").header("X-Token", token).executeString();
String sign = KsetSignUtil.of(secret).signSha1(params);
```

线程池按业务名隔离，并可通过 `MdcThreadPoolTraceAdapter` 透传 `Monitor` 链路上下文：

```java
KsetThreadPoolFactory factory = KsetThreadPoolFactory.getInstance();
factory.setGlobalTraceContextAdapter(new MdcThreadPoolTraceAdapter());
factory.register("order-payment", KsetThreadPoolFactory.PoolConfig.ioConfig());
factory.execute("order-payment", () -> callExternalApi());
```

结构化日志与流程日志消费 MDC 中的 `traceId`，不要在业务日志包里自建 TraceContext：

```java
private static final StructLog LOG = StructLog.of(OrderService.class);

String flowId = FlowLogContext.beginFlow("order_create", userId);
try {
    LOG.info("order create", "orderId", orderId);
    FlowLogContext.step("validate", FlowEventType.ENTER);
} finally {
    FlowLogContext.clear();
}
```

### 事件门面：Spring 本地事件 / RocketMQ

事件门面位于 `kset-common`，业务只依赖 `EventFacade`、`EventHandler`、`SendCallback`。默认自动配置为 Spring 本地事件实现；引入 `kset-starter-mq` 且配置 RocketMQ producer 后，会自动切换为 RocketMQ 实现。两种实现都支持普通、异步、延迟、顺序、事务提交后事件。

```java
@KsetMqEvent(topic = "order-event", tag = "created")
public record OrderCreatedEvent(Long orderId, Long userId) { }

@Service
public class OrderService {
    private final EventFacade eventFacade;
    private final RocketMqEventOperations rocketMqEvents;

    public OrderService(EventFacade eventFacade, RocketMqEventOperations rocketMqEvents) {
        this.eventFacade = eventFacade;
        this.rocketMqEvents = rocketMqEvents;
    }

    public void createOrder(Long orderId, Long userId) {
        OrderCreatedEvent event = new OrderCreatedEvent(orderId, userId);
        // 注解式：topic/tag 来自 @KsetMqEvent。
        eventFacade.publish(event);
        eventFacade.publishAsync(event, null);
        eventFacade.publishDelay(event, 30 * 60 * 1000L);
        eventFacade.publishOrderly(event, String.valueOf(userId));
        eventFacade.publishTransaction(event);

        // 编程式：调用方显式指定 topic/tag。
        rocketMqEvents.publish("order-event", "created", event);
        // 编程式：省略 topic 时使用 @KsetMqEvent 或 rocketmq.producer.topic。
        rocketMqEvents.publish(event);
    }
}

@Component
public class OrderCreatedHandler implements EventHandler<OrderCreatedEvent> {
    @Override
    public Class<OrderCreatedEvent> eventType() {
        return OrderCreatedEvent.class;
    }

    @Override
    public void handle(OrderCreatedEvent event) {
        // 执行业务消费
    }
}
```

框架已在事件发布和消费路径接入 `Monitor`，监控异常会被捕获并记录错误日志，不影响业务发布和消费流程。

### kset-starter-web：Web、统一响应、异常与 OpenAPI

```xml
<dependency>
    <groupId>com.kset</groupId>
    <artifactId>kset-starter-web</artifactId>
</dependency>
```

```java
@Tag(name = "用户")
@RestController
@RequestMapping("/api/users")
public class UserController {
    @Operation(summary = "按 ID 查询")
    @GetMapping("/{id}")
    public ApiResponse<UserEntity> get(@PathVariable Long id) {
        return ApiResponse.success(userService.getById(id));
    }
}
```

常用配置：

```yaml
knife4j:
  enable: true
springdoc:
  group-configs:
    - group: default
      paths-to-match: /api/**
kset:
  web:
    oplog:
      enabled: true
      user-id-header: X-User-Id
    request-logging:
      enabled: false
    response:
      trace-id-enabled: true
```

访问地址：`/doc.html`、`/v3/api-docs`。生产环境建议关闭或通过网关鉴权限制文档访问。

### kset-starter-monitor：全链路监控与 TraceId

```xml
<dependency>
    <groupId>com.kset</groupId>
    <artifactId>kset-starter-monitor</artifactId>
</dependency>
```

业务埋点统一使用 `com.kset.common.monitor.Monitor`：

```java
try (var tx = Monitor.newTransaction(MonitorTypes.BIZ, "createOrder")) {
    // 业务逻辑
    tx.setStatus(MonitorStatus.SUCCESS);
} catch (Exception e) {
    Monitor.logError(e, "createOrder failed");
    throw e;
}
```

异步或线程池场景使用 `Monitor.capture()` / `Monitor.openScope()`，避免丢失上下文日志 ID：

```java
TraceSnapshot context = Monitor.capture();
executor.execute(() -> {
    try (MonitorScope scope = Monitor.openScope(context)) {
        asyncWork();
    }
});
```

默认后端为本地 `LogBackend`。显式配置 `kset.monitor.backend=cat` 后才启用 CAT 后端：

```yaml
kset:
  monitor:
    enabled: true
    backend: log
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

### kset-starter-datasource：MyBatis-Plus 与 dynamic-datasource

```xml
<dependency>
    <groupId>com.kset</groupId>
    <artifactId>kset-starter-datasource</artifactId>
</dependency>
<dependency>
    <groupId>org.xerial</groupId>
    <artifactId>sqlite-jdbc</artifactId>
</dependency>
```

数据源能力统一引入 `kset-starter-datasource`，数据库类型由 JDBC 驱动决定。示例默认使用 SQLite，无需外部数据库；MySQL 或 PostgreSQL 项目可替换为 `mysql-connector-j` 或 `postgresql`。

```yaml
spring:
  datasource:
    url: jdbc:sqlite:./data/kset_demo.db
kset:
  datasource:
    enabled: true
    auto-fill: true
```

`createTime` / `updateTime` 自动填充由 datasource starter 注册；逻辑删除、Mapper 扫描使用 MyBatis-Plus / Spring Boot 标准配置扩展。Flyway 不属于 datasource starter 默认能力，业务如需数据库迁移请自行显式引入 Flyway 依赖与配置。新增 KSet 数据源配置请使用 `kset.datasource.*`。

### kset-starter-redis：缓存、锁、排行榜

```xml
<dependency>
    <groupId>com.kset</groupId>
    <artifactId>kset-starter-redis</artifactId>
</dependency>
```

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
kset:
  redis:
    key-prefix: "myapp:"
    default-ttl: 30m
    max-ttl: 7d
```

缓存写入必须带有效期；未显式传 TTL 时使用 `kset.redis.default-ttl`：

```java
redisService.setEx("order:" + order.getId(), order, Duration.ofMinutes(30));
Order cached = redisService.get("order:" + orderId, Order.class);
KsetRedis.of("cache").setEx("item:" + id, item, Duration.ofHours(1));
```

高危批量操作使用流式 API，避免 `KEYS` 或一次性加载大集合：

```java
redisService.scanKeys("order:*", keys -> redisService.deleteAll(keys));
redisService.hScan("user:100", entry -> process(entry));
```

Redisson 锁默认开启；如无需分布式锁，可配置 `kset.redis.redisson.enabled=false` 关闭：

```java
lockExecutor.runWithWait("order:" + orderId, Duration.ofSeconds(3), Duration.ofMinutes(2), () -> syncOrder(orderId));

@KsetLocked(value = "'order:' + #orderId", strategy = WAIT_THEN_FAIL, waitTime = "3s", lease = "2m")
public void syncOne(Long orderId) {
}
```

排行榜基于 ZSET：

```java
KsetRedisRankBoard board = rankService.board(
        KsetRedisRankOptions.builder("arena:2025-w20").ttl(Duration.ofDays(7)).build());
board.increment(userId, 10);
List<KsetRedisRankEntry> top100 = board.top(100);
```

### kset-starter-cache：一级/二级缓存门面

`kset-starter-cache` 不依赖 `kset-starter-redis`。只引入 cache starter 时可使用 L1 Caffeine；需要 Redis 二级缓存时再额外引入 `kset-starter-redis`，Redis 模块会自动注册 L2 适配器。

```xml
<dependency>
    <groupId>com.kset</groupId>
    <artifactId>kset-starter-cache</artifactId>
</dependency>
```

只使用 L1 时必须显式关闭默认 L2：

```yaml
kset:
  cache:
    default-layers: L1
    cache-null: true
    null-ttl: 1m
    l1:
      default-ttl: 5m
      maximum-size: 10000
```

使用 L1 + Redis L2：

```xml
<dependency>
    <groupId>com.kset</groupId>
    <artifactId>kset-starter-cache</artifactId>
</dependency>
<dependency>
    <groupId>com.kset</groupId>
    <artifactId>kset-starter-redis</artifactId>
</dependency>
```

```java
@KsetCacheable(cacheName = "user", key = "'user:id:' + #id", layers = {L1, L2})
public UserDTO getById(Long id) {
    return queryDb(id);
}

@KsetCaching(evict = {
        @KsetCacheEvict(cacheName = "user", key = "'user:id:' + #id", layers = {L1, L2}),
        @KsetCacheEvict(cacheName = "userByPhone", key = "'user:phone:' + #phone", layers = {L1, L2})
})
public void deleteUser(Long id, String phone) {
    deleteDb(id);
}
```

默认读顺序为 L1 -> L2 -> 方法加载；L2 命中会回填 L1。声明 L2 但没有 L2 适配器时，会过滤不可用层并回退到已有 L1；如果没有任何可用缓存层才会报错。

编程式 API 可注入 `KsetCacheFacade`，也可使用静态门面 `KsetCache`：

```java
KsetCacheSpec spec = KsetCacheSpec.builder("user", "user:id:" + id)
        .layers(L1, L2)
        .ttl(Duration.ofMinutes(10))
        .valueType(UserDTO.class)
        .build();

UserDTO user = KsetCache.getOrLoad(spec, UserDTO.class, () -> queryDb(id));
KsetCache.put(spec, user);
KsetCache.evict(spec);

KsetCacheMetrics metrics = KsetCache.metrics();
long hits = metrics.hits();
long misses = metrics.misses();
```

框架内置缓存监控：

- `Monitor` Transaction：`Cache/get.L1`、`Cache/get.L2`、`Cache/put.*`、`Cache/evict.*`
- `Monitor` Metric：`kset.cache.l1.hit`、`kset.cache.l2.hit`、`kset.cache.miss`、`kset.cache.load`、`kset.cache.put`、`kset.cache.evict`、`kset.cache.error`
- 本地快照：`KsetCache.metrics()` 返回命中、未命中、加载、写入、删除、异常计数
- 缓存监控异常只记录日志和错误指标，不影响业务流程

### kset-starter-nacos：注册发现与配置约定

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

`starter-nacos` 不传递 `starter-web` / `starter-sentinel`，业务按场景显式组合。

### kset-starter-sentinel：限流熔断规则

```xml
<dependency>
    <groupId>com.kset</groupId>
    <artifactId>kset-starter-sentinel</artifactId>
</dependency>
```

规则默认按应用名从 Nacos 加载，可通过 `kset.cloud.sentinel.*` 覆盖 dataId：

```yaml
kset:
  cloud:
    sentinel:
      enabled: true
      flow-rule-data-id: order-service-flow-rules
      degrade-rule-data-id: order-service-degrade-rules
```

### kset-starter-dubbo：RPC、TraceId 与灰度路由

```xml
<dependency>
    <groupId>com.kset</groupId>
    <artifactId>kset-starter-dubbo</artifactId>
</dependency>
```

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
      trace-propagation-enabled: true
      gray-metadata-key: version
      default-gray-tag: stable
```

Dubbo TraceId 透传和 RPC Transaction 由 `kset-starter-monitor` 的 Dubbo 插件提供；Dubbo 治理与路由冷启动由 `kset-starter-dubbo` 提供。

### kset-starter-gateway：网关、动态路由与灰度

Gateway 是独立进程，勿与 `kset-starter-web` 同用：

```xml
<dependency>
    <groupId>com.kset</groupId>
    <artifactId>kset-starter-gateway</artifactId>
</dependency>
<dependency>
    <groupId>com.kset</groupId>
    <artifactId>kset-starter-monitor</artifactId>
</dependency>
```

```yaml
kset:
  cloud:
    gateway:
      enabled: true
      route-data-id: order-gateway-gateway-routes
      sentinel-enabled: true
      auth-enabled: false
      cors-enabled: true
      trace-header: X-Trace-Id
      gray-header: X-Gray-Tag
```

动态路由 JSON 使用 Nacos dataId `{gateway-app}-gateway-routes`，格式见下文 “Gateway 动态路由示例”。

### kset-starter-mq：RocketMQ 组件依赖

```xml
<dependency>
    <groupId>com.kset</groupId>
    <artifactId>kset-starter-mq</artifactId>
</dependency>
```

当前 `kset-starter-mq` 负责提供 `rocketmq-v5-client-spring-boot-starter` 依赖，版本由 BOM 管理；本地环境使用 RocketMQ 5 Proxy gRPC 端点 `127.0.0.1:8081`，应用侧只需配置 RocketMQ 原生 `rocketmq.producer.endpoints` / `rocketmq.producer.topic`。引入 MQ starter 且存在 `RocketMQClientTemplate` 时，框架会自动注册 RocketMQ 版 `EventFacade`，并用 RocketMQ 消息承载 `publish` / `publishAsync` / `publishDelay` / `publishOrderly` / `publishTransaction`；业务通过 `@KsetMqEvent` 注解或 `RocketMqEventOperations` 编程式 API 控制 topic/tag，不需要额外声明事件门面配置。

### kset-cloud SPI：规则、灰度与网关鉴权扩展

自定义扩展实现接口并注册为 Spring Bean：

```java
@Component
public class OrderFlowRuleProvider implements CloudRuleProvider {
    @Override
    public CloudRuleType ruleType() {
        return CloudRuleType.SENTINEL_FLOW;
    }

    @Override
    public void onRuleChanged(String jsonContent) {
        // 规则变更后的扩展处理
    }
}
```

常用 SPI：

| SPI | 用途 |
|-----|------|
| `CloudRuleProvider` | 自定义 Sentinel / Dubbo / Gateway 规则变更处理 |
| `GrayTagResolver` | 自定义灰度标签解析 |
| `GatewayAuthProvider` | Gateway JWT / Token 鉴权 |

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
Client → Gateway (X-Gray-Tag) → LoadBalancer (metadata 匹配) → 微服务 (starter-monitor) → Dubbo (starter-monitor)
```

## 构建

```bash
mvn clean install
```

## 后续迭代

- 完整 JWT/OAuth2 Gateway 鉴权
- 可选：Knife4j / Redisson 改为 optional 依赖以进一步瘦身 classpath
