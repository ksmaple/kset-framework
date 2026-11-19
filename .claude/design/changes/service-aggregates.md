# 服务运行形态聚合变更记录

- feature-key：`service-aggregates`
- 当前 change-id：`service-aggregates-v4`
- 当前状态：`removed`
- 当前入口：N/A（业务按需直接依赖各 starter）

## 实现索引

| change-id | 状态 | 入口 | 替代目标 | 回滚来源 | 回滚可用性 | 清理条件 |
| --- | --- | --- | --- | --- | --- | --- |
| service-aggregates-v2 | removed | 两个聚合 POM | 单体聚合缺少 MQ 与 JDBC 驱动 | N/A | 不可用 | 已由 v3 收敛 JDBC 驱动 |
| service-aggregates-v3 | removed | 两个聚合 POM | v2 的三种 JDBC 驱动聚合 | N/A | 不可用 | 用户决定移除聚合构件 |
| service-aggregates-v4 | removed | N/A | v3 的两个聚合 POM | N/A | 不可用 | N/A |

## service-aggregates-v2

- 日期：2026-11-05
- 目标：补齐单体和 Dubbo 聚合的 Web、SQL、Redis、MQ 常用运行依赖。
- 判定时间：2026-11-05 14:16:36 +08:00
- 上线状态：开发未上线
- 判定方式：自动判定
- 判定证据：两个聚合 POM 仍为本地未跟踪文件。
- 证据来源：`git status --short`、`git ls-files --error-unmatch kset-starter-service-monolith/pom.xml`
- 当时代码状态：单体 POM 含 Web、鉴权、数据源、缓存、Redis、监控、调度，缺少 MQ 和 JDBC 驱动；Dubbo POM 依赖单体 POM 和 Dubbo starter；已知目标调用方为 kset-rag，尚未迁入聚合依赖。
- 兼容选择：N/A（未改变现有服务接口或行为）
- 回滚目标：N/A
- 旧版本正式线上主分支及对比基线 Git SHA：待确认
- 旧版正式发布版本：待确认
- 基线 Git ref：`a07d3f67ec1e63ae6ee83dae7c6abbc42ee1a91c`（不包含未提交的聚合 POM）
- 结果 Git ref：N/A（未提交）
- 回滚来源：N/A
- 回滚可用性：不可用（修改前聚合 POM 未提交）

| 文件 | 符号 | 职责 | 动作 | 状态 |
| --- | --- | --- | --- | --- |
| `kset-starter-service-monolith/pom.xml` | dependencies | 聚合常用服务依赖 | modify | active |
| `kset-starter-service-dubbo/pom.xml` | dependencies | 在单体聚合上增加 Dubbo | retain | active |
| `docs/usage/service-aggregates.md` | 服务运行形态聚合 | 接入与切换说明 | modify | active |
| `README.md` | 模块结构与能力表 | 聚合能力摘要 | modify | active |
| `.claude/skills/kset-framework-coder/references/project-spec.md` | 服务运行形态聚合 | 项目差异约定 | modify | active |
| `CHANGELOG.md` | Unreleased | 版本变更摘要 | modify | active |

### 回滚步骤

1. 聚合 POM 尚未提交，回滚需依据本记录与当前工作树手工移除本轮新增依赖。
2. 同步恢复接入说明、项目差异约定和版本摘要。

## service-aggregates-v4

- 日期：2026-11-05
- 目标：移除尚未提交的两个聚合模块，业务应用继续按需直接依赖框架 starter 与 JDBC 驱动。
- 判定时间：2026-11-05 +08:00
- 上线状态：开发未上线
- 判定方式：自动判定
- 判定证据：两个聚合 POM 和接入文档均未被 Git 跟踪。
- 证据来源：`git ls-files`、`git status --short`
- 当时代码状态：根 POM、父 POM、README、CHANGELOG 和本地 project-spec 已登记聚合入口；kset-rag 尚未迁入聚合依赖。
- 兼容选择：N/A（未改变已发布契约）
- 回滚目标：`service-aggregates-v3`
- 旧版本正式线上主分支及对比基线 Git SHA：待确认
- 旧版正式发布版本：待确认
- 基线 Git ref：`9dc7d78fea8c3a3e5eecab2cd7c7820bc1c3f866`（不包含未提交的聚合 POM）
- 结果 Git ref：N/A（未提交）
- 回滚来源：N/A
- 回滚可用性：不可用（聚合 POM 未提交）

| 文件 | 符号 | 职责 | 动作 | 状态 |
| --- | --- | --- | --- | --- |
| `kset-starter-service-monolith/pom.xml` | dependencies | 单体依赖聚合 | remove | removed |
| `kset-starter-service-dubbo/pom.xml` | dependencies | Dubbo 依赖聚合 | remove | removed |
| `docs/usage/service-aggregates.md` | 服务运行形态聚合 | 接入文档 | remove | removed |
| `pom.xml` | modules | reactor 注册 | modify | active |
| `kset-boot-parent/pom.xml` | dependencyManagement | 聚合版本管理 | modify | active |
| `README.md`、`docs/README.md`、`CHANGELOG.md` | 聚合条目 | 项目说明 | modify | active |
| `kset-starter-datasource/README.md` | KSet Datasource | 驱动接入说明 | modify | active |
| `.claude/skills/kset-framework-coder/references/project-spec.md` | 本地 / Dubbo 服务引用 | 项目差异约定 | modify | active |

### 回滚步骤

1. 若需重建聚合，重新设计依赖集合并在根 POM 与父 POM 注册。
2. 恢复相关接入文档并核对业务应用依赖。

## service-aggregates-v3

- 日期：2026-11-05
- 目标：聚合模块只内置 MySQL JDBC 驱动，移除 PostgreSQL 和 SQLite 运行时驱动。
- 判定时间：2026-11-05 +08:00
- 上线状态：开发未上线
- 判定方式：自动判定
- 判定证据：聚合 POM 仍为本地未跟踪文件。
- 证据来源：`git status --short`
- 当时代码状态：v2 单体聚合同时含 MySQL、PostgreSQL、SQLite 运行时驱动；Dubbo 聚合传递依赖单体聚合；kset-rag 仍使用 PostgreSQL 配置且尚未迁入聚合依赖。
- 兼容选择：N/A（未改变已发布契约）
- 回滚目标：`service-aggregates-v2`
- 旧版本正式线上主分支及对比基线 Git SHA：待确认
- 旧版正式发布版本：待确认
- 基线 Git ref：`a07d3f67ec1e63ae6ee83dae7c6abbc42ee1a91c`（不包含未提交的聚合 POM）
- 结果 Git ref：N/A（未提交）
- 回滚来源：N/A
- 回滚可用性：不可用（v2 聚合 POM 未提交）

| 文件 | 符号 | 职责 | 动作 | 状态 |
| --- | --- | --- | --- | --- |
| `kset-starter-service-monolith/pom.xml` | dependencies | 只聚合 MySQL JDBC 驱动 | modify | active |
| `kset-starter-service-dubbo/pom.xml` | dependencies | 传递依赖单体聚合 | retain | active |
| `docs/usage/service-aggregates.md` | 服务运行形态聚合 | 接入与数据库选择说明 | modify | active |
| `README.md` | 模块结构与能力表 | 聚合能力摘要 | modify | active |
| `kset-starter-datasource/README.md` | KSet Datasource | 数据库驱动接入说明 | modify | active |
| `.claude/skills/kset-framework-coder/references/project-spec.md` | 服务运行形态聚合 | 项目差异约定 | modify | active |
| `CHANGELOG.md` | Unreleased | 版本变更摘要 | modify | active |

### 回滚步骤

1. 聚合 POM 尚未提交，恢复 v2 需依据本记录在单体聚合中重新声明 PostgreSQL 与 SQLite 驱动。
2. 同步恢复接入说明、项目差异约定和版本摘要。
