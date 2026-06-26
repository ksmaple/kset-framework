# Codex Project Context

> 本文件是 Codex 薄索引。完整 AI 协作配置以 [.claude/AGENTS.md](.claude/AGENTS.md) 为单源真相。

## 先读

| 需要 | 入口 |
|------|------|
| 总体协作说明 | [.claude/AGENTS.md](.claude/AGENTS.md) |
| 全局规则 | [.claude/rules/kaka-project-rules.md](.claude/rules/kaka-project-rules.md) |
| 技能快速选择 | [.claude/skills/README.md](.claude/skills/README.md) |
| 架构与数据流 | [.claude/ARCHITECTURE.md](.claude/ARCHITECTURE.md) |

## 默认约束

- Codex 从 `.agents/skills/` 读取技能；该目录应链接到 `.claude/skills/`。
- 克隆或链接异常时执行 `python scripts/py/init.py links`。
- 文档、规则、技能正文只维护在 `.claude/`。
