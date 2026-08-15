# kset-framework AI 协作配置

> 本文件是 Codex 索引。完整 AI 协作配置以 [.claude/AGENTS.md](.claude/AGENTS.md) 为单源真相。

## 技能

- 研发：`kset-framework-coder` → `.claude/skills/kset-framework-coder/`
- 修复：`kset-framework-fixer` → `.claude/skills/kset-framework-fixer/`

## 多环境

| 环境 | 技能路径 | 索引 |
|------|----------|------|
| Claude | `.claude/skills/` | `CLAUDE.md` |
| Codex | `.agents/skills/`（链接） | `AGENTS.md` |
| Cursor | `.cursor/skills/`（链接） | `.cursor/CLAUDE.md` |

## 使用指引

- 实现代码 → `kset-framework-coder`
- 修复 → `kset-framework-fixer`
- 项目内规范/API/DDD 设计 → `kset-framework-coder`，并将约定写入 `project-spec.md`
