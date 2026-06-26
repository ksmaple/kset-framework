# kset-framework AI 协作配置

> 本文件是项目 AI 协作主索引。规则、技能与设计文档统一维护在 `.claude/`。

## 规则

- [kaka-project-rules](rules/kaka-project-rules.md)

## 技能

- 研发：`kset-framework-coder` → `skills/kset-framework-coder/`
- 修复：`kset-framework-fixer` → `skills/kset-framework-fixer/`
- 规范：`kaka-coder-designer` → `skills/kaka-coder-designer/`
- Git 提交：`kaka-util-git-commit` → `skills/kaka-util-git-commit/`

## 三端

| 环境 | 技能路径 | 索引 |
|------|----------|------|
| Claude | `.claude/skills/` | `CLAUDE.md` |
| Codex | `.agents/skills/` → `.claude/skills/` | `AGENTS.md` |
| Cursor | `.cursor/skills/` → `.claude/skills/` | `.cursor/CLAUDE.md` |

如链接缺失，执行 `python scripts/py/init.py links`。
