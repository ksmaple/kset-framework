# KSet 公共框架（kset-framework）AI 协作配置

## 规则

- [kaka-project-rules](rules/kaka-project-rules.md)（含 R021–R029 书写约定；R030 改码授权；R036–R038 脚本与编码）

## 技能

| 场景 | 技能 | 路径 |
|------|------|------|
| 实现代码 | `kset-framework-coder` | `.claude/skills/kset-framework-coder/` |
| 修错 | `kset-framework-fixer` | `.claude/skills/kset-framework-fixer/` |
| 规范与写码编排 | `kaka-coder-designer` | `.claude/skills/kaka-coder-designer/` |
| Git 提交/推送 | `kaka-util-git-commit` | `.claude/skills/kaka-util-git-commit/`（仅用户明确要求；可用 `/kaka-util-git-commit`） |

项目差异见 [project-spec.md](skills/kset-framework-coder/references/project-spec.md)。

## 多环境

| 环境 | 技能路径 | 索引 |
|------|----------|------|
| Claude | `.claude/skills/` | 根 `CLAUDE.md` |
| Codex | `.agents/skills/`（链接） | 根 `AGENTS.md` |
| Cursor | `.cursor/skills/`（链接） | `.cursor/CLAUDE.md` |

三端技能链接（在 **kset-developer** 仓库根执行，已 `npm install` 后）：

```bash
npm run setup:ai-env-links -- --repo-root k:/git_pro/kset-framework
```

从平台同步 `kaka-coder-designer`、`kaka-util-git-commit`（守卫确认 OVERWRITE 后加 `--force`）：

```bash
npm run copy:init-skills -- k:/git_pro/kset-framework --force
```

## 使用指引

**实现代码** → `kset-framework-coder`  
**修复** → `kset-framework-fixer`  
**新模块/域规范设计** → `kaka-coder-designer` → 可选 `.claude/design/` → `kset-framework-coder`  
**提交** → `kaka-util-git-commit`（用户要求 commit/push 时）
