---
name: kaka-util-git-commit
description: 极简 Git 提交：分支、中文 commit；功能名与提交标题仅据变更路径列表推断，不读 diff/文件内容。触发：(1) git commit (2) 特性分支 (3) 中文提交 (4) 明确要求 push。
---

# Kaka Util Git Commit

> 层级边界见 [SKILL-HIERARCHY.md](../SKILL-HIERARCHY.md)。执行细则：[git-commit-spec.md](references/git-commit-spec.md)

## 触发条件

**启用**：用户明确要求执行 git commit；或 slash `/kaka-util-git-commit`（可选附带提交说明、feature-slug）；或明确要求 push 且本技能已处理 commit。

**不启用**：无变更可提交；仅查看 `git status` / `log` / `diff`；用户明确「只暂存不提交」；用户未要求 commit 且未调用本技能。

## 核心规则

R000: 未确认有变更且用户意图为提交前，禁止执行 `git commit`
R001: `git commit` 仅在用户明确要求或调用本技能时执行
R002: `git push` **仅当**用户明确要求 push、description/命令含 push、或用户消息含「推送」时执行（禁止默认自动 push）
R003: 目标分支 `<target>` = `feat/dev-{yyyyMMdd}-{功能}-{作者}`；大改动插入 `{NN}`；细则见 git-commit-spec R040–R051
R004: 当前分支已承载本次推断的同一功能（`feat/dev-*` 解析出的 feature+author 与路径推断一致）时**不切换、不新建**，直接提交；否则按 spec R049c/d 切换已有或 R050 从 `{primary}` 新建，禁止从 dev/develop/`feat/dev-*` 拉新分支
R005: 变更一致性、同文件双改、冲突标记未通过则终止，禁止 `git add -A`（见 spec R020–R033）
R006: 提交信息格式 `<type>: <中文标题>`，标题 ≤50 字，type 为 feat/fix/docs/style/refactor/test/chore/perf
R007: 禁止 `--no-verify`、`--force`、`--force-with-lease`
R008: 敏感文件（`.env`、`*.pem` 等）须用户确认后再暂存
R009: 多仓库场景须用户确认操作对象仓库
R010: 步骤级命令与判定以 [git-commit-spec.md](references/git-commit-spec.md) 为准
R011: **极简推断**：`{feature-slug}` 与提交标题仅据 `git status` 路径列表（及用户显式说明）；禁止为推断执行 `git diff`/`git show` 或 Read 变更文件；细则 [minimal-commit-inference.md](references/minimal-commit-inference.md)

## 工作流

```
Step 1: 检测 Git 仓库（spec R001–R003）
Step 2: 检查变更；无变更则退出（spec R010–R012）
Step 2.5: 变更一致性；疑似多主题则用户选 206-a/b/c（spec R020–R023）
Step 2.6: 同文件双改与冲突标记；未通过则终止（spec R030–R033）
Step 3: 路径推断 feature → 同功能则留驻当前分支，否则切换/创建 <target>（spec R039–R052）
Step 4: 暂存；已有 staged 则复用（spec R060–R063）
Step 5: 据路径列表生成 `{feature-slug}` 与中文提交信息（spec R070–R079；minimal-commit-inference）
Step 6: git commit（spec R080–R082）
Step 7: 仅当 R002 允许时 git push（spec R090–R094）
Step 8: 自检清单（spec R110–R114）并输出分支、hash、推送结果（若有）
```

## 参考文件

| 文件 | 说明 |
|------|------|
| [git-commit-spec.md](references/git-commit-spec.md) | 步骤级 R 条文（仓库、分支、暂存、提交、推送） |
| [minimal-commit-inference.md](references/minimal-commit-inference.md) | 路径→feature-slug、type、中文标题（不读 diff） |

## Collaboration

| 场景 | 技能 |
|------|------|
| 业务代码 | `{proj}-coder` |
| 修错后提交 | `{proj}-fixer` → 本技能（用户要求 commit/push 时） |
| AI 改码 worktree 隔离 | `kaka-project-rules` R039；commit 前须在会话 worktree 内完成改码 |
| 项目 init | `kaka-utils-project-init` → `npm --prefix scripts run copy:init-skills` 复制本技能到业务 `.claude/skills/` |
