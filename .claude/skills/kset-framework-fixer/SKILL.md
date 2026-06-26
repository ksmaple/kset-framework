---

name: kset-framework-fixer

description: "kset-framework 修复技能，proj=kset-framework。触发：build 失败、测试失败、Lint、缺陷修复。仅改本仓。"

---



# kset-framework-fixer



> 层级见 [SKILL-HIERARCHY.md](../SKILL-HIERARCHY.md)。规范：[fix-spec.md](../kaka-coder-designer/references/fix-spec.md) · [project-spec.md](../kset-framework-coder/references/project-spec.md) · [coordination.md](../kaka-coder-designer/references/coordination.md)



## 触发条件



**启用**：编译失败、测试失败、Lint 告警、运行时缺陷、用户明确要求修错；保持 `kset-framework-coder` 已约定的风格。



**不启用**：新功能开发 → `kset-framework-coder`；领域/API 规范设计 → `kaka-coder-designer`；git commit/push → `kaka-util-git-commit`。



## 核心规则



R001: 先读 project-spec 与 fix-spec（X 域），保持与 `kset-framework-coder` 风格一致

R002: 最小变更，禁止借机大面积重构

R003: 修复后须 `mvn -q -DskipTests compile` 通过；相关测试仅在用户、任务或 CI 显式要求时通过 `mvn -q test` 验证

R004: 输出含根因、策略、影响范围；不确定时列出待确认项

R005: 命名/API 类问题对照 naming-spec 与 project-spec

R006: 显式测试时默认只写单个 Controller 接口的真实 Spring 环境单点调用测试；仅无可调用 Controller 或用户点名 Service 时才写 Service 方法测试

R007: 显式测试须通过日志输出目标、上下文、入参与返回结果，禁止复杂流程、断言、Mock 与统计型报告



## 工作流



```

Step 0: 确认任务为修错（新功能 → kset-framework-coder）

Step 1: 读 fix-spec、project-spec；复现失败信息

Step 2: 定位根因，拟定最小补丁

Step 3: 修改代码并运行 `mvn -q -DskipTests compile`；用户/任务/CI 显式要求时再运行 `mvn -q test`，测试形态见 test-execution-spec

Step 4: 输出修复摘要；用户要求 commit 时交 kaka-util-git-commit

```



## 命令

| 用途 | 命令 | 说明 |
|------|------|------|
| 编译 | `mvn -q -DskipTests compile` | 默认必须执行 |
| 测试 | `mvn -q test` | 仅用户/任务/CI 显式要求时执行 |

## Collaboration



| 场景 | 技能 |

|------|------|

| 新功能/扩展 | `kset-framework-coder` |

| 规范细则 | `kaka-coder-designer`（fix 域） |

| Git 提交 | `.claude/skills/kaka-util-git-commit/`（用户要求 commit/push 时） |
