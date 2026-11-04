# KSet 文档中心

这里统一汇总业务接入、模块参考、设计契约和项目维护文档。场景文档提供典型接入路径与最小示例；完整配置、SPI 和实现边界以对应模块 README 及源码为准。

## 快速入口

- **首次接入**：查看[项目概览与快速开始](../README.md#快速开始)，再按下方场景选择文档。
- **确认版本**：Java、Maven 和依赖基线见 [kset-boot-parent](../kset-boot-parent/README.md)，当前构件版本以 `kset-boot-parent/pom.xml` 的 `<version>` 为准。
- **升级版本**：先读[版本发布说明](../CHANGELOG.md)中的行为变化与升级指引。
- **查找配置或 API**：进入对应模块 README；公开对象字段可检索[当前代码对象与字段](design/current-object-fields.md)。

## 场景使用文档

| 我要做的事 | 文档 |
|------------|------|
| 统一 HTTP 响应、业务异常与操作日志 | [Web 统一响应](usage/web.md) |
| Web 登录态、多套鉴权与网关可信请求头 | [登录鉴权](usage/auth.md) |
| 一批独立任务并行执行、限制并发 | [并行批跑](usage/parallel.md) |
| HTTP / RPC 瞬时失败重试 | [重试](usage/retry.md) |
| 跨线程传递登录态与 Trace | [请求上下文](usage/context.md) |
| Redis 读写、Key 规范与强制 TTL | [Redis 读写](usage/redis.md) |
| 使用 Redis 分布式锁 | [Redis 分布式锁](usage/redis-lock.md) |
| 使用本地或 Redis 多级缓存 | [多级缓存](usage/cache.md) |
| 发布 Spring 本地事件或 RocketMQ 消息 | [事件](usage/events.md) |
| TraceId、业务埋点与跨线程观测 | [监控与 TraceId](usage/monitor.md) |
| 接入 MyBatis-Plus 单库或多数据源 | [数据源](usage/datasource.md) |
| 接入 Nacos、Sentinel 与 Dubbo | [云服务](usage/cloud.md) |
| 配置独立 Gateway 鉴权、CORS 与灰度 | [网关](usage/gateway.md) |
| 配置集群唯一运行的定时任务 | [Schedule 模块](../kset-starter-schedule/README.md) |
| 接入 Agent 循环、协议与恢复机制 | [Agent Core](../kset-agent-core/README.md) |
| 按业务上下文组装 Agent 与 MCP 工具 | [Agent Runtime](../kset-agent-runtime/README.md) |

## 模块参考

| 模块 | 文档 | 主要内容 |
|------|------|----------|
| `kset-boot-parent` | [模块 README](../kset-boot-parent/README.md) | Maven parent / BOM、Java 与依赖版本基线 |
| `kset-common` | [模块 README](../kset-common/README.md) | 公共工具、事件门面、上下文、线程池、重试与日志 |
| `kset-cloud` | [模块 README](../kset-cloud/README.md) | 云服务公共配置、Nacos 命名、灰度与规则 SPI |
| `kset-starter-web` | [模块 README](../kset-starter-web/README.md) | Web 自动配置、统一响应、异常与操作日志 |
| `kset-starter-auth` | [模块 README](../kset-starter-auth/README.md) | 登录态、多套鉴权与上下文透传 |
| `kset-starter-monitor` | [模块 README](../kset-starter-monitor/README.md) | 监控门面、TraceId、指标与健康检查 |
| `kset-starter-schedule` | [模块 README](../kset-starter-schedule/README.md) | 受管定时任务与 SQL 集群唯一运行锁 |
| `kset-starter-datasource` | [模块 README](../kset-starter-datasource/README.md) | JDBC、MyBatis-Plus 与动态数据源 |
| `kset-starter-cache` | [模块 README](../kset-starter-cache/README.md) | 多级缓存、缓存注解与编程式 API |
| `kset-starter-redis` | [模块 README](../kset-starter-redis/README.md) | Redis 操作、分布式锁、排行榜与 Cache L2 |
| `kset-starter-nacos` | [模块 README](../kset-starter-nacos/README.md) | Nacos 注册发现、配置与规则监听 |
| `kset-starter-sentinel` | [模块 README](../kset-starter-sentinel/README.md) | Sentinel 规则加载与边界 |
| `kset-starter-dubbo` | [模块 README](../kset-starter-dubbo/README.md) | Dubbo RPC、标签路由与治理规则 |
| `kset-starter-gateway` | [模块 README](../kset-starter-gateway/README.md) | Gateway 动态路由、灰度、鉴权与限流 |
| `kset-starter-mq` | [模块 README](../kset-starter-mq/README.md) | RocketMQ 事件门面与监控 |
| `kset-agent-core` | [模块 README](../kset-agent-core/README.md) | Agent 循环内核、ReAct、协议与恢复扩展 |
| `kset-agent-runtime` | [模块 README](../kset-agent-runtime/README.md) | Agent 按次组装、Skill、业务上下文与 MCP 工具适配 |

## 设计与源码契约

| 文档 | 说明 |
|------|------|
| [资源权限规范](design/resource-permission.md) | 功能码与资源 ACL 两平面、判定顺序和授权条目；本仓暂无实现 |
| [当前代码对象与字段](design/current-object-fields.md) | 各模块公开数据对象、配置对象的字段类型、初始值和源码入口 |
| [Agent 对象与字段契约](../kset-agent-core/docs/core-object-contract.md) | Agent 核心对象职责、所有权、空值与持久化边界 |
| [Agent Runtime 对象与字段契约](../kset-agent-runtime/docs/runtime-object-contract.md) | 运行时组件、业务上下文与工具适配字段 |
| [Agent JSON v1 协议](../kset-agent-core/docs/protocols/agent-json-v1.md) | 内置协议的字段、动作与错误码 |
| [Agent 协议扩展指南](../kset-agent-core/docs/protocol-extension-guide.md) | 自定义 Codec、版本兼容和扩展动作接入 |
| [kset-rag 接入映射](../kset-agent-core/docs/integration/kset-rag.md) | Agent Core 与宿主 RAG 工程的职责映射 |

## 项目维护与协作

| 文档 | 说明 |
|------|------|
| [项目根 README](../README.md) | 仓库结构、能力总览、版本矩阵、快速开始与构建 |
| [版本发布说明](../CHANGELOG.md) | 版本变更、升级指引与发布流程 |
| [Git 工作流](git-workflow.md) | 主工作树、提交范围、提交信息与推送约定 |
| [AI 协作配置](../AGENTS.md) | 项目技能、协作规则与多平台索引 |
