# kset-framework 项目专属规范

> 本文件由历史项目配置恢复；有明确源码或文档来源的事实以当前版本核对，自动探测的默认值须标为待核实。单个功能的迭代历史写入对应需求文档或 `docs/{module}/changes/{feature-key}.md`，本文件最多保留定位索引。执行约束以 [项目规则](../../../.claude/rules/kset-framework-project-rules.md) 为准；适用的 Kset 全局技能见 [项目主索引](../../../.claude/AGENTS.md)。

**proj**：`kset-framework` · **backend** · 生成：2026-08-27 17:51:27

## 1. 画像


- 项目规范：`kset-framework-requirements-designer`、`kset-framework-file-relations`、`kset-framework-coder`、`kset-framework-fixer`、`kset-framework-tester`、`kset-framework-api-doc`、`kset-framework-environment`、`kset-framework-git-commit`、`kset-framework-requirements-discussion`、`kset-framework-scenario-analysis`、`kset-framework-product-spec`、`kset-framework-ui-ux-spec`、`kset-framework-technical-spec`、`kset-framework-delivery-feedback`、`kset-framework-delivery-config`
- coder / fixer：`kset-framework-coder`、`kset-framework-fixer`
- 技术栈：Java + Maven + Redis
- SQL：框架的数据访问能力由 `kset-starter-datasource` 提供；具体使用方的目标数据库方言待核实
- API：按 `kset-starter-web`、`kset-starter-gateway`、`kset-starter-dubbo` 各模块的现有契约

## 2. 项目约定整合


| 主题            | 状态               | 本项目实际做法                            |
| ------------- | ---------------- | ---------------------------------- |
| Naming | 按模块 | 公共包及 starter 名称沿用 `com.kset` 与 `kset-starter-*` 的现有命名 |
| DDD | 按模块 | 本仓库是基础框架多模块工程，不设单一业务聚合 |
| API | 按模块 | Servlet MVC、WebFlux Gateway 与 Dubbo 分别由 `kset-starter-web`、`kset-starter-gateway`、`kset-starter-dubbo` 承载 |
| Frontend | N/A | 本仓库无前端应用模块 |
| SQL | 按使用方核实 | `kset-starter-datasource` 提供数据访问能力；旧 `.claude/sql-dialect.json` 由无信号默认值生成，不能作为目标方言依据 |
| Conversion | 按模块 | DTO 与持久化映射沿用各 starter 的已有实现，不定义跨模块转换层 |
| Event | 按模块 | 消息集成能力位于 `kset-starter-mq` |
| Cache | 已确认 | `kset-starter-cache` 提供 Caffeine L1 缓存，`kset-starter-redis` 提供 Redis 能力 |
| Orchestration | 按模块 | Dubbo、Gateway、Nacos、Sentinel 分别位于对应 starter |
| Distributed | 已确认 | `kset-starter-schedule` 使用 SQL 锁表与 fencing token；Redis 锁能力位于 `kset-starter-redis` |
| Log | 按模块 | Web、Gateway 与 Monitor 的链路处理分别位于对应 starter |
| Engineering   | 已确认             | 仓库内 `mvn validate` 执行 Enforcer、Checkstyle 与 PMD；`mvn verify` 另执行包括包级 ArchUnit 在内的测试，规则清单见 `docs/code-quality-gates.md` |



> 已确认存在大 Key、热点 Key 或分片，或根据增长预计产生大 Key 时，记录项目实际阈值与监控来源、大 Key 切片键与单片上限、稳定路由、扩容及迁移切换、聚合及原子性边界；无法安全切片或五种常用类型不适用时，记录备选方案及用户选择。没有实际证据时保持待确认，不预设固定大小或分片数量。

> 已确认使用 Redis Cluster 时，记录配置来源、客户端与节点发现方式、DB 0、槽路由和故障切换、超时及重试边界、需同槽的最小业务单元；未核实的部署事实保持待确认，不把依赖声明当作实际集群用法。

> 使用 Redis 的项目在 Cache 行或「关键文件与目录」记录公共业务 Redis 工具与中间适配层的项目内相对路径、适用能力及已确认的 Key、有限 TTL、适用的原子过期能力、周期性 Key 的短暂补设窗口、失败告警与有界修复、仅业务允许时清理及结果核验、存量无 TTL Key 的迁移核验状态、续期与覆盖写约定、序列化约定；未找到实际调用依据时标为待确认，不推断某个客户端依赖就是业务入口。

## 3. 时间格式（前后端）

> init 必填。默认 wire：`yyyy-MM-dd HH:mm:ss`；后端 Jackson；前端 string + dayjs `YYYY-MM-DD HH:mm:ss`。与默认不一致须在差异项说明。

| 项 | 约定 | 探测来源 / 说明 |
| --- | --- | --- |
| API wire 格式 | yyyy-MM-dd HH:mm:ss | 默认 yyyy-MM-dd HH:mm:ss；存量格式差异见下行 |
| 时区策略 | Asia/Shanghai（默认；如与存量配置不一致请在差异项调整） | 默认 Asia/Shanghai；对应 spring.jackson.time-zone |
| Epoch 单位（若用） | N/A | 秒 / 毫秒；字段后缀如 AtMs |
| 后端序列化 | Jackson：date-format=yyyy-MM-dd HH:mm:ss + JavaTimeModule | 默认 Jackson：date-format=yyyy-MM-dd HH:mm:ss + JavaTimeModule |
| 前端日期库 | N/A | 如 dayjs，格式 YYYY-MM-DD HH:mm:ss；无前端填 N/A |
| SQL 列类型 | 待核实 | 以使用方数据库方言与表结构为准 |
| 存量差异 | 框架不统一持有业务时间字段 | 各 starter 与使用方分别核对序列化契约 |

## 4. 关键文件与目录

> 仅记录项目内相对路径、用途与必要说明，帮助项目规范快速定位入口；禁止写入机器相关绝对路径。

| 路径 | 用途 | 说明 |
| --- | --- | --- |
| `docs` | 项目文档 | 自动探测 |
| `pom.xml` | Maven 构建与依赖入口 | 自动探测 |
| `.mvn/maven.config` | 仓库内默认启用代码质量检查 | `mvn validate` 不编译或执行测试 |
| `.mvn/checkstyle.xml` | Checkstyle 项目规则集 | 由 `kset-boot-parent/pom.xml` 引用 |
| `.mvn/pmd-ruleset.xml` | PMD 项目规则集 | 由 `kset-boot-parent/pom.xml` 引用 |
| `docs/code-quality-gates.md` | Java/Maven 代码质量门禁规范 | 规则范围、命令及发布边界 |
| `README.md` | 项目入口说明 | 自动探测 |
| `kset-boot-parent` | 子项目或模块 | 检测到 pom.xml |
| `kset-cloud` | 子项目或模块 | 检测到 pom.xml |
| `kset-common` | 公共基础类型与工具 | 包含分页结果及公共常量，供业务模块显式依赖 |
| `kset-tuya` | 可选涂鸦适配模块 | 独立于框架核心；为保持涂鸦客户端调用包名沿用 `com.kset.common.tuya` API，依赖 Fastjson2 |
| `kset-starter-auth` | 子项目或模块 | 检测到 pom.xml |
| `kset-starter-cache` | 子项目或模块 | 检测到 pom.xml |
| `kset-starter-datasource` | 子项目或模块 | 检测到 pom.xml |
| `kset-starter-dubbo` | 子项目或模块 | 检测到 pom.xml |
| `kset-starter-gateway` | 子项目或模块 | 检测到 pom.xml |
| `kset-starter-monitor` | 子项目或模块 | 检测到 pom.xml |
| `kset-starter-schedule` | 定时任务唯一运行锁（@KsetScheduled/@KsetTaskLock + SQL 锁表） | 1.0.13 新增 |
| `kset-starter-mq` | 子项目或模块 | 检测到 pom.xml |
| `kset-starter-nacos` | 子项目或模块 | 检测到 pom.xml |
| `kset-starter-redis` | 子项目或模块 | 检测到 pom.xml |
| `kset-starter-sentinel` | 子项目或模块 | 检测到 pom.xml |
| `kset-starter-agent-harness` | AgentScope Harness 的 Spring 装配与按次构造入口 | 1.0.17 已发布，标签 v1.0.17 |
| `kset-starter-image` | 图片 AI 能力：多模态理解（转文字/参数提取）与图片生成，OpenAI 兼容协议 | 1.0.17 已发布，标签 v1.0.17 |
| `kset-architecture-tests` | 跨模块 ArchUnit 架构测试 | 测试模块，不安装或发布 |

## 5. 外部引用（轻量记录）

> 仅记录外部文档、外部项目或跨仓文件的名称、位置、用途和关联目录；不复制外部正文，不记录凭据、令牌或带查询参数的敏感 URL。未发现时保留“无”。

| 类型 | 名称 | 位置 | 用途 | 关联本地文件/目录 |
| --- | --- | --- | --- | --- |
| 外部项目 | ksmaple/kset-framework | `https://github.com/ksmaple/kset-framework` | README 中的项目仓库链接 | `README.md` |
| 外部项目 | kset-rag | 本机 `../kset-rag`（kset-rag-server 多模块 Maven 工程） | 业务侧项目，通用能力提炼来源（2026-08-29 分析） | 无（提炼时产出至各 starter） |
| 外部项目 | AgentScope Java | `https://github.com/agentscope-ai/agentscope-java` | Harness 与原生扩展 API，固定 2.0.5 | `kset-starter-agent-harness` |
| 外部源码 | Apache Dubbo 3.3.6 RpcException / AbstractClusterInvoker | `https://github.com/apache/dubbo/tree/dubbo-3.3.6` | 核对无可用提供者及 RPC 故障码 | `kset-starter-dubbo` |
| 外部文档 | 阿里云 Topic 与 Tag 最佳实践 | `https://help.aliyun.com/zh/apsaramq-for-rocketmq/cloud-message-queue-rocketmq-4-x-series/use-cases/best-practices-of-topics-and-tags` | Topic/Tag 分类原则，按本仓 V5 行为核对适用范围 | `docs/usage/rocketmq.md` |
| 外部文档 | 阿里云订阅关系一致 | `https://help.aliyun.com/zh/apsaramq-for-rocketmq/cloud-message-queue-rocketmq-4-x-series/use-cases/subscription-consistency` | Consumer Group 订阅一致性参考 | `docs/usage/rocketmq.md` |
| 外部协议 | OpenAI Images / 官方 OpenAPI | `https://github.com/openai/openai-openapi/blob/master/openapi.yaml` | 2026-11-24 核对 JSON 生成、编辑请求及模型字段 | `kset-starter-image` |
| 外部文档 | 阿里云万相文生图 API | `https://help.aliyun.com/zh/model-studio/text-to-image-v2-api-reference` | 核对万相原生异步接口与 OpenAI 协议差异 | `kset-starter-image/README.md` |

## 构建与基础设施兼容基线

| 组件 / 环境 | 实际版本或管理方式 | 探测来源 |
| --- | --- | --- |
| 构建工具 / Java | Maven 3.9+、Java 21 | `kset-boot-parent/pom.xml` |
| Spring Boot / Cloud | Spring Boot 3.5.14、Spring Cloud 2025.0.2、Spring Cloud Alibaba 2025.0.0.0 | `kset-boot-parent/pom.xml` |
| 数据库 | 按使用方模块与目标环境核实 | 原方言文件标注为无信号默认值，不作为兼容基线 |
| Dubbo | 3.3.6 | `kset-boot-parent/pom.xml`、`kset-starter-dubbo/pom.xml` |
| RocketMQ Spring Starter | 2.3.5 | `kset-boot-parent/pom.xml` |
| Elasticsearch Client | 8.13.0 | `kset-boot-parent/pom.xml` |
| Redis 客户端 | Jedis 5.1.2、Redisson 3.40.2 | `kset-boot-parent/pom.xml` |
| ORM | MyBatis 3.5.15、MyBatis-Plus 3.5.5 | `kset-boot-parent/pom.xml` |
| MySQL JDBC | MySQL Connector/J 8.3.0 | `kset-boot-parent/pom.xml` |
| 父 POM / BOM | 当前开发 `com.kset:kset-boot-parent:1.0.18-SNAPSHOT`；最新发布 1.0.17 | `kset-boot-parent/pom.xml`、各模块 `pom.xml`；v1.0.17 标签及 Nexus POM |
| AgentScope Harness | `io.agentscope:agentscope-harness:2.0.5`，由父 POM 管理 | `kset-boot-parent/pom.xml`、`kset-starter-agent-harness/pom.xml` |

- `kset-starter-web` 为 Servlet MVC，`kset-starter-gateway` 为 WebFlux Gateway，二者异常入口独立。
- 分组二级榜是 R112 的单项例外：经用户本次明确授权，仅 `KsetRedisZSetGroupRankBoard` 可使用 Redis Lua，使组内成员 ZSET 与分组总榜 ZSET 的贡献、成员移除和分组移除成为单条原子操作；Key 必须同槽。其他 Redis 业务脚本仍受 R112 限制。

## 6. 差异项

- 框架各 starter 服务不同类型的使用方；业务表结构和 API wire 契约由使用方核对。

### 版本发布约定

- 标准流程：**maven-release-plugin**（`mvn -B release:prepare release:perform`，根 pom 已配置标签格式 `v@{}`、跳过测试参数与 `-Pnexus`）；手工 `versions:set + deploy` 仅作备选。
- 两种方式均须手工同步 `kset-boot-parent/pom.xml` 的 `kset-framework.version` 自定义属性（插件不处理），发布前改为发布版、发布后改回下一 SNAPSHOT。
- 每次发布须在 `CHANGELOG.md` 顶部补充版本说明；详细步骤与失败恢复见 `CHANGELOG.md`「如何发布新版本」。

### 新模块准入清单

1. 骨架：pom 继承 `kset-boot-parent`；包根 `com.kset.{领域}`；零内部依赖优先，依赖 common 需有实际 import。
2. 注册：自动装配用 `META-INF/spring/...AutoConfiguration.imports`，环境默认值用 `META-INF/spring/...EnvironmentPostProcessor`；禁用老式 spring.factories。
3. 配置：`kset.{领域}.*` 前缀 + `enabled` 开关（matchIfMissing=true）；元数据由 parent 的 configuration-processor 自动生成，无需手写。
4. 生命周期：持有线程/连接/订阅资源的组件继承 `AbstractKsetLifecycleComponent` 并按相位表接入统一启停。
5. 边界：公共入口必须校验入参（null/空/非正数）；会导致语义静默失效的输入（如非正 atMostFor）必须 fail-fast；注解时长等可解析参数在启动期预检。
6. 线程命名：`kset-` 前缀。
7. 弃用：内部 @Deprecated 方法的调用方同步标记（详见弃用约定）。
8. 收尾：根 pom 模块注册 + parent dependencyManagement + README 模块表 + project-spec 差异项 + CHANGELOG + 变更记录。

### 弃用约定

- 框架内部标记 `@Deprecated` 的方法，门面/适配层（静态门面、Service 委托层）调用时必须同步标记 `@Deprecated`，否则构建会持续产生自伤型弃用警告；回滚保留方法中的弃用调用用 `@SuppressWarnings({"unused","deprecation","removal"})` 显式抑制并在注释说明。

### cloud 配置载体下沉（1.0.15）

- `KsetRedisProperties`（`kset.redis.*`）归属 `kset-starter-redis` 的 `com.kset.redis.config`；`KsetDatasourceProperties`（`kset.datasource.*`）归属 `kset-starter-datasource` 的 `com.kset.datasource.config`；cloud 旧类 @Deprecated 保留兼容，配置键不变。
- monitor 自持灰度/链路键（`kset.monitor.dubbo.trace-propagation-enabled`、`kset.monitor.dubbo.default-gray-tag`、`kset.monitor.gateway.trace-header`），未配置时经 `KsetMonitorCloudCompat` 回退 `kset.cloud.*` 旧键；monitor 不再依赖 kset-cloud。
- kset-cloud 收敛为纯云规则层（CloudRuleProvider SPI / NacosConfigConvention / 灰度 LB），服务 dubbo/gateway/nacos/sentinel。
- 历史变更记录：Git `d3d133237b7a09decb41f9488d9b4fedc7688cdf` 中的 `.claude/design/changes/cloud-config-decouple.md`；当前工作树未恢复该文件。

### 本地 / Dubbo 服务引用


- 外部业务模块引用业务服务接口字段时默认使用 `@KsetReference`；仅固定本地或远端、或需要注解未覆盖的 Dubbo 专有参数时使用原生注入，并在业务接入说明中记录原因。普通 Spring 基础设施依赖不属于此约定；正式接入说明见 `docs/usage/service-reference.md`。
- 模块：`kset-common` 独立提供 `com.kset.common.service.KsetReference` 注解与 Spring Bean 字段注入，默认仅选择本地 Bean；`kset-starter-dubbo` 提供可选远端适配器。启动时优先按 Spring 候选与 `@Primary` / `@Qualifier` 规则注入本地 Bean；仅宿主机引入 Dubbo starter 且配置注册中心、无本地候选时创建 Dubbo 引用，不在调用失败后切换。
- 注解的 `group`、`version` 支持 Spring 配置占位符，不参与本地 Bean 选择，用于远程引用与冲突校验；同一接口与 group/version 的远程引用复用并在容器销毁时释放。非接口、静态或 final 字段启动失败。
- 宿主机不引入 Dubbo starter 或未配置 Dubbo 注册中心地址时，本地 Bean 可直接使用，不创建远端引用或执行冲突探测；没有本地 Bean 时提示缺少服务与可用远端适配器并启动失败。引入并配置 Dubbo 后重启重新选择：本地 Bean 仍优先，缺少本地 Bean 才使用 Dubbo 引用。
- `dubbo.registry.address=N/A` 视为无注册中心；使用 Nacos 配置但暂不接入 Dubbo 的应用可显式设置该值，避免 starter 从 Nacos 地址自动补齐注册中心。
- 已配置注册中心且选中本地 Bean 的引用在应用 Runner 阶段校验 Dubbo 提供者：本机导出地址不算冲突，其他进程存在同接口、group、version 提供者则启动失败；无法读取目录或注册中心也启动失败。

### 定时任务约定

- 模块：`kset-starter-schedule`。两个独立注解：`@KsetScheduled`（元注解继承原生 `@Scheduled`，全量透传调度属性，纳入管理）与 `@KsetTaskLock`（唯一运行锁，仅需要集群单活时独立声明）。
- 区分约定：原生 `@Scheduled` = 本机任务每实例执行；`@KsetScheduled` = 受管任务；叠加 `@KsetTaskLock` = 集群唯一运行。
- 锁实现：SQL 锁表 `t_kset_schedule_lock`（UPDATE 过期抢占 → INSERT 兜底，fencing_token 自增，时间以数据库为准）；每次获取使用独立 `locked_by` 标识，同线程释放时仅更新本次持有的锁；**启动时自动识别数据库方言（PostgreSQL/MySQL/MariaDB，其他回退通用 DDL）并自动建表**，无需迁移脚本；如需 Flyway 统一管理可配 `kset.scheduler.lock.auto-create-table=false`。
- 配置：`kset.scheduler.lock.enabled/table-name/at-most-for/auto-create-table`；全局 at-most-for 默认 10m，注解可 per-task 覆盖。
- 历史变更记录：Git `d3d133237b7a09decb41f9488d9b4fedc7688cdf` 中的 `.claude/design/changes/scheduled-task-lock.md`；当前工作树未恢复该文件。

### 图片 AI 能力约定

- 模块：`kset-starter-image`，包根 `com.kset.image`；对外入口为 `vision.VisionClient`（图片理解/转文字/参数提取）与 `generation.ImageGenerator`（文生图/图生图），业务方只依赖接口与请求/结果模型，不依赖供应商客户端。
- 协议：理解使用 `/chat/completions`，生成使用 `/images/generations`，参考图编辑使用 `/images/edits` 的 JSON `images[].image_url` 格式；默认 base-url 为 OpenAI。其他服务须实现对应协议；不适配万相原生异步任务或 multipart 编辑协议。
- 配置：`kset.image.{enabled,base-url,api-key}` 共用；`kset.image.vision.*` 与 `kset.image.generation.*` 各自独立 `enabled` 开关、模型与超时；未配置 api-key 时不装配客户端并在启动时告警，不阻断应用启动。
- 结构化参数提取：`VisionRequest.responseSchema` 为提示词结构说明，不执行 JSON Schema 校验；JSON 对象解析进 `VisionResult.structuredParams` 并保留 null 字段，解析失败回退为空 Map 并保留 `rawText`。
- 失败口径：网络错误、供应商拒绝与响应解析失败统一抛 `ImageException`；日志关键词 `[kset-image]`；图片与 prompt 原值不记日志。
- 边界：不内嵌本地 OCR 库（图片识别由多模态模型完成）；启用时预检客户端配置，空白密钥不装配客户端；base64 与本地文件按内容识别 MIME。当前配置与接入示例见 `kset-starter-image/README.md`；修复与验证状态见 `docs/kset-starter-image/changes/image-client.md`。

### RocketMQ Topic/Tag 约定

- 规划依据与使用规则见 `docs/usage/rocketmq.md`：相关业务、相同消息类型且时效和流量相近时优先共用 Topic，以稳定 Tag 区分事件类别；消息类型、业务、时效或流量需要隔离时拆 Topic。同一 Consumer Group 的订阅条件保持一致。
- `kset-starter-mq` 内置消费者默认订阅默认 Topic 的 `*` Tag，Group 为 `${spring.application.name:kset}-event-consumer`，可通过 `kset.mq.event.default-consumer-enabled=false` 关闭。业务按 Tag 消费时继承 `RocketMqTagEventConsumer<T>`，在具体 Spring Bean 上声明 `@RocketMQMessageListener` 的 Topic、Tag 和业务专属 Group；单个监听器只执行一个业务处理，失败仅重试该 Group，业务处理须具备幂等性。事件注解的 Tag 只决定发送目标。`publishTransaction` 是 Spring 提交后发送普通消息，不是 RocketMQ 事务消息。
- `RocketMqEventOperations.publish(topic, event)` 只覆盖 Topic 并保留事件注解 Tag；三参数重载显式控制 Tag。通配消费者先匹配处理器再反序列化，默认确认未匹配或本应用缺少对应事件类的消息；仅在 Group 应处理订阅内全部事件时使用 `kset.mq.event.fail-on-unhandled=true` 返回失败。`kset.event.enabled=false` 关闭 KSet 的 MQ 门面和通配消费者，不影响业务直接声明的 RocketMQ 监听器；MQ 异步回调恢复发布时 Trace 并在退出时还原客户端线程原值。

### 统一启停（优雅启停）

- 机制：基于 Spring `SmartLifecycle` 相位编排，核心包 `kset-common/src/main/java/com/kset/common/lifecycle/`；启动按相位升序、停机按相位降序。
- 相位约定（`KsetLifecyclePhases`）：定时任务（ContextClosedEvent 最先取消）→ `PHASE_READINESS=600`（readiness 置为 REFUSING_TRAFFIC，K8s 探针摘流）→ Web 断流（server.shutdown=graceful）→ `PHASE_TRAFFIC=500`（Dubbo `DubboBootstrap.stop()` 摘流 + MQ 停收）→ `PHASE_EVENT=400`（事件执行器排空）→ `PHASE_THREAD_POOL=300`（业务线程池排空）→ `PHASE_MONITOR=200`（监控 flush）→ `PHASE_INFRA=100`（Redis 连接）→ DB 连接池在 Spring destroy 阶段最后关闭。
- 停机策略：排空 + 超时兜底（`KsetGracefulShutdowns`：shutdown → awaitTermination → shutdownNow）；`DisposableBean.destroy()` 入口通过 `shutdownLifecycle()` 幂等复用同一停机逻辑。
- 配置：`kset.lifecycle.enabled`（默认 true）、`kset.lifecycle.shutdown-timeout`（默认 30s）、`kset.lifecycle.health.enabled`（默认 true，装配 `KsetLifecycleHealthIndicator`，需业务侧引入 actuator）；`KsetLifecycleEnvironmentPostProcessor` 低优先级注入 `server.shutdown=graceful`、`spring.lifecycle.timeout-per-shutdown-phase=30s`；`KsetDubboEnvironmentPostProcessor` 注入 `dubbo.application.shutwait=10000`；probes/liveness/readiness 默认值由 `kset-starter-monitor` 的 `KsetHealthEnvironmentPostProcessor` 注入（均仅在用户未配置时生效）。
- 组件生命周期 Bean 命名：`Kset{组件}Lifecycle`，继承 `AbstractKsetLifecycleComponent`；新组件接入时按上表选相位并在所属 starter 自动配置中注册。
- 业务侧扩展：**默认统一使用函数式适配器** `new KsetLifecycleHookAdapter(name, phase, stopAction)` 注册为 Bean；逻辑复杂时才实现 `KsetLifecycleHook` 接口（方式二）或继承 `AbstractKsetLifecycleComponent`（方式三，框架内部组件使用）；相位可用常量加偏移插入内置相位之间；注册后自动进入相位编排与 `ksetLifecycle` 健康明细。
- 历史变更记录：Git `d3d133237b7a09decb41f9488d9b4fedc7688cdf` 中的 `.claude/design/changes/graceful-lifecycle.md`；当前工作树未恢复该文件。

### Agent Harness 模块约定

- 模块 `kset-starter-agent-harness`，包根 `com.kset.agent.harness`；依赖 AgentScope Java 2.0.5 的 `agentscope-harness`，不依赖旧 `kset-agent-core/runtime`。
- `AgentHarnessFactory#builder` 为公共入口，每次调用创建独立 `HarnessAgent.Builder`；调用方完成按次配置、构建并关闭 agent，提供原生 `Model`、`Toolkit`、`AgentStateStore`、工作区和运行配置。配置前缀 `kset.agent.harness`，`enabled` 默认开启。
- 工厂关闭 AgentScope 默认 `AgentTraceMiddleware`，注册只记录固定阶段、完成/失败/取消及耗时的 `AgentHarnessTimingMiddleware`，不记录消息、模型文本、工具参数/结果或异常消息。业务模型 Token/费用仍由宿主记录。
- `AgentHarnessToolkitFactory` 接受原生 `RuntimeContext`、`ToolkitConfig` 和按次 `AgentHarnessToolSource`；验证身份和工具名，拒绝同轮重名，再构建原生 `Toolkit`。工具可见性不授予权限，实际调用仍由宿主校验。
- 公共层只设无业务权限的默认构造选项，不持有用户会话、业务工具、审批、账本、Web/SSE 或数据库状态；状态与回滚由宿主实现 `AgentStateStore` 及其检查点契约。原生 `Hook`、`Middleware` 等扩展按 AgentScope API 由调用方注入。
- 新模块无业务 wire 时间字段；目录为 `src/main/java/com/kset/agent/harness`、`src/main/resources/META-INF/spring`。历史变更记录见 Git `d3d133237b7a09decb41f9488d9b4fedc7688cdf` 中的 `.claude/design/changes/agent-harness.md`；当前工作树未恢复该文件。

## 8. 环境连接与日志索引

> 仅记录经配置或部署事实核对的环境标识、项目内相对配置路径、连接别名、密钥引用和日志入口；不得写入密码、令牌、私钥、完整带凭据 URI 或日志正文。未登记的环境由 `kset-framework-environment` 按实际项目配置定位，确认后补齐；本索引不代表连接已通过核验。

| 环境 | 服务 / 组件 | 配置来源或连接别名 | 凭据引用 | 日志入口 | 最近核验 |
| --- | --- | --- | --- | --- | --- |
| 未登记 | N/A | 按项目当前配置定位 | N/A | N/A | 未核验 |
| 本机 Docker | Nacos 2.5.2 / Redis 7.4.9 / MySQL 8.0 / PostgreSQL 17 / RocketMQ 5.5 | `env/compose.yaml`；单容器 `kset-local-infra`；仅 `127.0.0.1`，端口见 `env/README.md` | 本机开发无鉴权 | `docker logs kset-local-infra` | 2026-11-06：单容器及组件健康、数据卷迁移已核验 |

本机 Docker 公共组件的适用范围、连接方式、数据保留与操作步骤以 `env/README.md` 为准；该环境不承载业务应用或预置业务数据。

AI 默认可读本机 Docker 的 `env/README.md`、`env/compose.yaml` 与本索引（2026-11-21 初始化确认）。不默认连接运行中的容器，也不默认读取其中的数据。其他环境待用户点名后才进入可读范围。

## 9. 部署入口与回滚索引

> 仅记录经项目发布配置或部署资料确认的非敏感入口和版本来源；部署授权仍以当前对话与项目规则为准。未登记或未核验不代表可以执行部署，禁止写入凭据、私有令牌、完整发布日志或临时产物路径。

| 环境 | 服务 / 模块 | 部署入口与方式 | 版本 / 产物来源 | 健康检查 | 回滚入口 | 最近核验 |
| --- | --- | --- | --- | --- | --- | --- |
| 未登记 | N/A | 按项目当前发布流程定位 | N/A | N/A | N/A | 未核验 |
| 本机 Docker | Nacos / Redis / MySQL / PostgreSQL / RocketMQ | `docker compose -f env/compose.yaml up -d --build --wait`；`env/compose.legacy.yaml` 仅留作配置参考 | `env/single/Dockerfile` 构建本地镜像 `kset-local-infra:latest` | 容器内 `kset-local-healthcheck` 与 `docker compose -f env/compose.yaml ps` | 旧数据卷已删除；当前数据需从单容器卷备份恢复 | 2026-11-06：旧容器及旧卷已清理，单容器健康已核验 |
| Maven 构件发布 | 根聚合与各可发布模块 | 根 `pom.xml` profile `nexus`；仓库 id `kset-nexus-releases`、`kset-nexus-snapshots` | 正式版 1.0.17 / 标签 v1.0.17；当前开发版本以父 POM 为准 | 18 个模块的 Nexus POM 及适用 JAR 均返回 200，版本与内部依赖属性核对通过 | 回滚步骤见 `CHANGELOG.md`；已发布版本禁止覆盖 | 2026-11-24：release:prepare / release:perform 成功，全仓 verify 通过 |

无常设改配置或部署授权（2026-11-21 初始化确认）。修改配置、部署或服务启停仍须在当前对话点名目标，并遵守项目规则 R043、R044、R128、R129。本索引不授予这些操作。

## 10. 测试公共配置基线

> 仅记录从项目配置、环境索引与已确认契约核实的非敏感共用值及其来源；init 无法确认的复杂配置保留待核实，首次获授权测试时补齐本次用例必需项。配置变化时记录新基线及受影响用例，测试结果引用实际使用的基线；禁止记录密码、签名密钥、动态令牌或带凭据的连接 URI。本节是项目事实索引，不构成连接或运行测试授权。

| 配置项 | 固定值或引用 | 来源与适用范围 | 版本 / 核验时间 | 状态 |
| --- | --- | --- | --- | --- |
| 请求编码与参数组织 | 默认按 Full POST + HTTP 200 + errCode | 项目接口约定；逐接口核实 | init 探测 | 待核实 |
| 测试环境与服务入口 | 待核实 | 见环境连接与日志索引 | 未核验 | 待核实 |
| 签名或登录态构造方式 | 待核实 | 现有认证配置与测试机制；不记录密钥 | 未核验 | 待核实 |
| 共用测试数据与业务键 | 待核实 | 真实测试环境有界读取或已授权准备 | 未核验 | 待核实 |
| 代表性接口连通证据索引 | `NOT_RUN` | 有适用 HTTP 测试时引用用例结果，不保存完整执行记录 | 未执行 | 待运行授权 |
