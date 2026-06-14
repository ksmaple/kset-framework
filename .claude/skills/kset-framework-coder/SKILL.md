---
name: kset-framework-coder
description: "KSet 公共框架（框架库）研发，proj=kset-framework。触发：实现功能、改业务代码、按 project-spec 与 coder-designer 编码。仅改本仓。"
---

# kset-framework-coder

> 层级见 [SKILL-HIERARCHY.md](../SKILL-HIERARCHY.md)。[project-spec.md](references/project-spec.md) + `.claude/skills/kaka-coder-designer/references/`

| 项 | 值 |
|----|-----|
| proj | `kset-framework` |
| 类型 | **框架库（Spring Boot Starter 聚合仓）** |

## 触发条件

**启用**：在本仓实现功能、修改业务源码、按 project-spec 与 coder-designer 域 spec 编码。

**不启用**：纯规范设计无写码 → `kaka-coder-designer`；编译/测试失败修错 → `kset-framework-fixer`；git commit/push → `kaka-util-git-commit`；平台 init → `kaka-utils-project-init`（在 kset-developer 维护）。

## 核心规则

R001: 先读 project-spec.md，再按需读 ../kaka-coder-designer/references/{domain}-spec.md
R002: 规范冲突以 **project-spec** 为准，其次 coder-designer references
R003: **实现业务代码时只用本技能**；规范来自 project-spec + kaka-coder-designer
R004: 新模块约定写入 project-spec §3；实现对照 coder-designer 对应域
R005: 禁止引用**平台仓库外**绝对路径；规范在 `.claude/skills/kaka-coder-designer/`
R006: 禁止替代 coder-designer 做未约定的架构决策
R007: 编译/验证：`mvn -q -DskipTests compile`
R008: 编写类名、方法名、API 操作名及入参/出参字段时须**简单易懂**，优先业务人员可理解的日常用语，避免晦涩缩写（见 `naming-spec.md` N028）
R009: 改码前须先输出变更计划并获用户授权，禁止未经确认直接改文件（见 `kaka-project-rules` R030）

## 工作流

```
Step 0: 确认任务为写码（否则路由到其他技能）
Step 1: 读 project-spec.md；按需读 coder-designer 域 *-spec.md
Step 2: 输出变更计划 → 用户授权（R030）→ 列任务清单（R007，可豁免时声明）
Step 3: 实现代码，遵守 project-spec、域 spec 与 N028（名称与入参/出参易懂）
Step 4: 执行 mvn -q -DskipTests compile；失败则交 kset-framework-fixer
Step 5: 用户明确要求时交 kaka-util-git-commit
```

## 参考文件

| 文件 | 说明 |
|------|------|
| [project-spec.md](references/project-spec.md) | 项目差异与约定 |
| [coordination.md](../kaka-coder-designer/references/coordination.md) | 域协作 · 写码编排 |

## Collaboration

| 场景 | 技能 |
|------|------|
| 规范设计 | `kaka-coder-designer` |
| 修错 | `kset-framework-fixer` |
| Git 提交 | `kaka-util-git-commit`（用户要求 commit/push 时） |
