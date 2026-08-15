# kset-framework AI 协作配置

> 本文件是项目 AI 协作主索引。项目技能与 project-spec 统一维护在 `.claude/skills/`，不依赖平台公共技能或规则。

## 技能

- 研发：`kset-framework-coder` → `skills/kset-framework-coder/`
- 修复：`kset-framework-fixer` → `skills/kset-framework-fixer/`

## 三端

| 环境 | 技能路径 | 索引 |
|------|----------|------|
| Claude | `.claude/skills/` | `CLAUDE.md` |
| Codex | `.agents/skills/` → `.claude/skills/` | `AGENTS.md` |
| Cursor | `.cursor/skills/` → `.claude/skills/` | `.cursor/CLAUDE.md` |

`.agents/skills` 与 `.cursor/skills` 仅定义为指向 `.claude/skills` 的目录链接；项目技能不依赖任何链接脚本。
