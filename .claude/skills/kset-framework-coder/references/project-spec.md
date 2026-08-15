# kset-framework 项目专属规范

> init 生成。本文件是目标项目技能的项目约定单源，不依赖平台公共技能目录。

**proj**：`kset-framework` · **backend** · 生成：2026-08-14 22:40

## 1. 画像

- coder / fixer：`kset-framework-coder`、`kset-framework-fixer`
- 技术栈：Java 21 + Maven 聚合 POM + Spring Boot Starter；BOM 在 `kset-boot-parent`
- SQL：mysql 8.0（`.claude/sql-dialect.json`）；本仓提供数据源 Starter，不承载业务库
- API：HTTP 200 + `ApiResponse<T>` + 业务 `errCode`（`kset-starter-web`）

## 2. 项目约定整合

| 主题 | 状态 | 本项目实际做法 |
| --- | --- | --- |
| Naming | 已确认 | 模块 `kset-starter-*` / `kset-common` / `kset-cloud`；包根与模块目录对应；类名、方法名与入参/出参须简单易懂 |
| DDD | N/A | 框架库，不按业务限界上下文拆聚合；按能力拆 Starter |
| API | 已确认 | 统一异常与 `com.kset.web.response.ApiResponse`；业务仓默认 Full POST |
| Frontend | N/A | 无前端 |
| SQL | 已确认 | `kset-starter-datasource`：JDBC + MyBatis-Plus + dynamic-datasource；方言 mysql，列类型 DATETIME |
| Conversion | N/A | 本仓不定义业务 DTO/Entity/PO 转换 |
| Event | 已确认 | `kset-starter-mq`：RocketMQ 事件门面，topic/tag 约定 |
| Cache | 已确认 | `kset-starter-cache` 多级缓存（L1 Caffeine，L2 SPI）；`kset-starter-redis` 为 Spring Data Redis |
| Orchestration | 已确认 | Nacos、Sentinel、Dubbo、Gateway Starter 提供注册、限流熔断与 RPC |
| Log | 已确认 | `kset-starter-monitor`：TraceId / 灰度 / 线程池 MDC |
| Engineering | 按需 | 容量、熔断与发布约定由使用方业务仓落地 |

## 3. 时间格式（前后端）

> init 必填。默认 wire：`yyyy-MM-dd HH:mm:ss`；后端 Jackson；前端 string + dayjs `YYYY-MM-DD HH:mm:ss`。与默认不一致须在差异项说明。

| 项 | 约定 | 探测来源 / 说明 |
| --- | --- | --- |
| API wire 格式 | yyyy-MM-dd HH:mm:ss | `KsetWebMvcConfigurer` 设置 `simpleDateFormat` 与 `DateFormatter` |
| 时区策略 | Asia/Shanghai | 与业务仓 `GMT+8` / `serverTimezone=Asia/Shanghai` 对齐 |
| Epoch 单位（若用） | N/A | 默认字符串日期，不用裸 epoch |
| 后端序列化 | Jackson + `JsonUtil` + `DateHelper` / `DateZoneHelper` | 仅用 Spring Boot 内置 Jackson；内部存储/透传为普通 JSON 字符串，不写类型元数据，不引入 Fastjson |
| 前端日期库 | N/A | 无前端 |
| SQL 列类型 | DATETIME | mysql 8.0 |
| 存量差异 | 无 | 与默认 wire 一致 |

## 4. 关键文件与目录

> 仅记录项目内相对路径、用途与必要说明，帮助项目技能快速定位入口；禁止写入机器相关绝对路径。

| 路径 | 用途 | 说明 |
| --- | --- | --- |
| `pom.xml` | 根聚合 POM | 模块清单与发布跳过 |
| `README.md` | 模块与包名约定 | 入口说明 |
| `kset-boot-parent` | 版本 BOM | Boot / Spring Cloud / Dubbo 版本 |
| `kset-common` | 公共工具 | 异常、日期、HTTP、线程池、`JsonUtil` |
| `kset-cloud` | 云服务规范 | `kset.cloud.*`、SPI |
| `kset-starter-web` | Web + 统一异常 | `ApiResponse`、全局异常、日期格式 |
| `kset-starter-auth` | 登录态与鉴权 | 多套鉴权与上下文透传 |
| `kset-starter-datasource` | 数据源 | JDBC + MyBatis-Plus |
| `kset-starter-cache` | 多级缓存门面 | Caffeine + SPI |
| `kset-starter-redis` | Redis | Spring Data Redis |
| `kset-starter-nacos` | 注册/配置 | 灰度 LoadBalancer |
| `kset-starter-sentinel` | 限流熔断 | 规则从 Nacos 拉取 |
| `kset-starter-dubbo` | RPC | TraceId 透传与标签路由 |
| `kset-starter-gateway` | 网关 | 动态路由 + Sentinel |
| `kset-starter-mq` | 消息 | RocketMQ 事件门面 |
| `kset-starter-monitor` | 监控 | TraceId / 灰度 / MDC |

## 5. 外部引用（轻量记录）

> 仅记录外部文档、外部项目或跨仓文件的名称、位置、用途和关联目录；不复制外部正文，不记录凭据、令牌或带查询参数的敏感 URL。未发现时保留“无”。

| 类型 | 名称 | 位置 | 用途 | 关联本地文件/目录 |
| --- | --- | --- | --- | --- |
| 外部项目 | ksmple/kset-framework | https://github.com/ksmaple/kset-framework | 远程源码仓 | `README.md` |

## 6. 差异项

- 本仓是框架库而非业务应用：无 `src/main/java` 根目录、无业务 Controller；写码落在各 Starter / `kset-common`。
- 业务仓通过 Maven 依赖本仓 Starter，不在本仓实现具体业务用例。
- JSON 只使用 Spring Boot 内置 Jackson。Redis / 鉴权头等内部序列化为普通字符串，不写 `@type` / `@class`，不引入 Fastjson。
