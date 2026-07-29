# kset-framework AI 协作配置

> 本文件是 Claude 索引。完整 AI 协作配置以 [.claude/AGENTS.md](.claude/AGENTS.md) 为单源真相。

## 规则

- [kaka-project-rules](.claude/rules/kaka-project-rules.md)

## 技能

- 研发：`kset-framework-coder` → `.claude/skills/kset-framework-coder/`
- 修复：`kset-framework-fixer` → `.claude/skills/kset-framework-fixer/`
- 规范：`kaka-coder-designer` → `.claude/skills/kaka-coder-designer/`
- Git 提交：`kaka-util-git-commit` → `.claude/skills/kaka-util-git-commit/`

## 多环境

| 环境 | 技能路径 | 索引 |
|------|----------|------|
| Claude | `.claude/skills/` | `CLAUDE.md` |
| Codex | `.agents/skills/`（链接） | `AGENTS.md` |
| Cursor | `.cursor/skills/`（链接） | `.cursor/CLAUDE.md` |

## 使用指引

- 实现代码 → `kset-framework-coder`
- 修复 → `kset-framework-fixer`
- 规范/API/DDD 设计 → `kaka-coder-designer`
- Git 提交 → `kaka-util-git-commit`（仅用户明确要求 commit/push 时）
