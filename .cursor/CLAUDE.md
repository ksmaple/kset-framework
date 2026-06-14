# kset-framework — Cursor 索引

配置单源：[.claude/AGENTS.md](../.claude/AGENTS.md)

技能：`.cursor/skills/` → `.claude/skills/`

## 权限策略

- 默认允许 AI 执行常见文件操作（读/写/编辑/删除/搜索）与项目内终端命令（git/python/mvn/npm/node 等）。
- 以下场景须先询问用户确认：
  - 进入计划模式（Plan Mode）或编排多步骤工作流
  - 执行跨仓库、跨项目或影响外部服务的操作
  - 删除/覆盖敏感文件（如 `.env`、密钥、凭据）
  - 执行 `git push`、发布、部署等不可撤销的向外操作
  - 修改 `.claude/rules/`、`AGENTS.md`、`.cursor/CLAUDE.md` 等全局协作配置

## 技能路由

- 写码 → `kset-framework-coder`
- 修错 → `kset-framework-fixer`
- 规范/API/DDD 设计 → `kaka-coder-designer`
- Git 提交 → `kaka-util-git-commit`（用户明确要求时）
- 项目 init → `kaka-utils-project-init`（在 kset-developer 维护）
