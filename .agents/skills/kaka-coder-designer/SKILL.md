---
name: kaka-coder-designer
description: 研发规范统一技能：命名、DDD、API、SQL、前端、转换、事件、缓存、编排、日志、编码纪律、修复、工程控制。触发：领域建模、契约/DDL 设计、规范对齐、可变集合/防御性拷贝、常见编码反模式、修错策略。不替代 {proj}-coder 写业务代码。
---

# Kaka Coder Designer

> 层级边界见 [SKILL-HIERARCHY.md](../SKILL-HIERARCHY.md)。平台规范单源；业务项目由 `kaka-utils-project-init` 整包复制到 `.claude/skills/kaka-coder-designer/`。
> 书写格式：[format-convention.md](references/format-convention.md) · 域协作：[coordination.md](references/coordination.md)

## 定位

将原 L2/L3/L4 设计技能**合并为单技能 + 分域 references**，供设计与实现阶段共用同一套编号规则。

| 负责 | 不负责 |
|------|--------|
| 字段语义、领域模型、API/SQL/事件等**规范与门禁** | 业务项目源码实现（→ `{proj}-coder`） |
| 指导 DDL、Controller/DTO、Converter 等**落地形态** | 仓库 init、复制技能（→ `kaka-utils-project-init`） |
| 修复策略与根因分析口径（fix 域） | Git 提交/推送（→ `kaka-util-git-commit`） |

**优先级（业务项目）**：`{proj}-coder/references/project-spec.md` > 本技能 `references/*-spec.md` > 存量代码习惯（init 探测写入 project-spec）。

## 触发条件

**启用**：用户要求领域建模、表结构/DDL、API 契约、缓存 Key、事件/Outbox、对象转换边界、日志/慢日志、编排/熔断、工程门禁、命名争议、编码纪律（缓存/全局可变集合、防御性拷贝、常见反模式）、修复策略；或 `{proj}-coder` / `{proj}-fixer` 需查规范细则。

**不启用**：

- 仅写/改业务代码且无规范争议 → `{proj}-coder`
- 编译失败、测试失败、Lint、安全补丁执行 → `{proj}-fixer`（细则仍读本技能 fix 域）
- 初始化业务仓、生成 coder/fixer → `kaka-utils-project-init`
- 新建/打包平台技能 → `kaka-utils-skill-creator`

## 核心规则

R000: 业务项目须先读 `project-spec.md`，再按需打开域 spec
R001: 字段业务含义**唯一**以 [naming-spec.md](references/naming-spec.md) 为准；各域禁止重定义语义
R002: 设计顺序：naming（已有语义）→ ddd → sql / api / event / cache / conversion / frontend / log / orchestration
R003: DDD 只写语义字段，禁止 SQL 列名、URL、DTO 映射表（见 ddd 域 D009）
R004: 默认 Full POST + HTTP 200 + `errCode`（api 域）；网关/服务间 429 见 orchestration 域
R005: SQL 从 DDD 机械映射 snake_case；写 DDL 前读 `sql-dialect.json`（sql 域）
R006: 除 sql（及项目约定的 ddd 文档路径）外，**不产出** `design/*` 文档；落地以代码为准
R007: 事件跨边界、Outbox、幂等见 event 域；Saga 发事件须 Outbox
R008: 修复最小变更、须编译与相关测试通过（fix 域）；实现由 `{proj}-fixer` 执行
R009: 正文禁止内联大段规范；细则只在 `references/*-spec.md`
R010: 禁止引用**平台仓库外**绝对路径；业务项目内用 `.claude/skills/kaka-coder-designer/`
R011: 复杂或有争议设计须先与用户确认（ddd D016）
R012: 技能/规则条文用 `R001: 描述`（全局强制见 `kaka-project-rules` R021–R029）；域 spec 用 `PREFIXNNN: 描述`（见 format-convention 第三节）

## 工作流

```
Step 0: 确认场景（设计 vs 实现 vs 修复）→ 不对则路由到其他技能
Step 1: 读 project-spec（若有）与 naming-spec
Step 2: 若涉持久化/接口 → 读 ddd-spec，补齐语义字段
Step 3: 按任务打开对应域 *-spec.md（可并行多域）
Step 4: 输出设计结论 / 变更清单 / 待确认项
Step 5: 实现阶段移交 {proj}-coder；修错移交 {proj}-fixer
```

## 参考文件

| 域 | 文件 | 编号前缀 | 要点 |
|----|------|----------|------|
| naming | [naming-spec.md](references/naming-spec.md) | N | 字段语义真理源 |
| ddd | [ddd-spec.md](references/ddd-spec.md) | D | 限界上下文、聚合、领域事件语义 |
| api | [api-spec.md](references/api-spec.md) | A | Full POST、ApiResult、幂等 |
| sql | [sql-spec.md](references/sql-spec.md) | S | DDL、方言、deleted/ver |
| frontend | [frontend-spec.md](references/frontend-spec.md) | F | TS 与 API JSON 对齐 |
| conversion | [conversion-spec.md](references/conversion-spec.md) | V | DTO/Entity/PO 边界 |
| event | [event-spec.md](references/event-spec.md) | E | EventMessage、Outbox |
| cache | [cache-spec.md](references/cache-spec.md) | K | Key、TTL、Cache-Aside |
| orchestration | [orchestration-spec.md](references/orchestration-spec.md) | O | 熔断、限流、Saga |
| log | [log-spec.md](references/log-spec.md) | L | traceId、慢日志、脱敏 |
| coding | [coding-spec.md](references/coding-spec.md) | C | 可变集合、防御性拷贝、常见反模式 |
| fix | [fix-spec.md](references/fix-spec.md) | X | 根因、最小变更、分类策略 |
| engineering | [engineering-spec.md](references/engineering-spec.md) | G | 容量、CI 门禁、灰度 |

**写码阶段**（`{proj}-coder`）：[codegen-task-spec.md](references/codegen-task-spec.md) · [compile-check-spec.md](references/compile-check-spec.md) · [test-execution-spec.md](references/test-execution-spec.md) · 设计检查 [design-lint-spec.md](references/design-lint-spec.md)

## Collaboration

| 场景 | 技能 |
|------|------|
| 写业务代码 | `{proj}-coder` + project-spec + 本技能 references |
| 修错落地 | `{proj}-fixer` + fix-spec |
| 复制到业务仓 | `kaka-utils-project-init` → `python scripts/py/init.py copy`（含 `kaka-util-git-commit`） |
| 域间依赖与 HTTP/幂等口径 | [coordination.md](references/coordination.md) |
| 提交代码 | `kaka-util-git-commit`（仅用户明确要求时） |

详表见 [coordination.md](references/coordination.md)。
