# kset-framework AI 协作配置

> 本文件是 Kimi 与 Codex 共用索引。完整 AI 协作配置以 [.claude/AGENTS.md](.claude/AGENTS.md) 为单源真相。

## 规则

- 项目规则：[.claude/rules/kset-framework-project-rules.md](.claude/rules/kset-framework-project-rules.md)
- 项目事实：[project-spec.md](.claude/standards/kset-framework-coder/references/project-spec.md)
- Git 工作流：[docs/git-workflow.md](docs/git-workflow.md)。直接在主工作树 `main` 修改、提交并推送；不创建任务分支或 worktree。

## AI 规则产物

- 先读 `artifacts/INDEX.json` 的 `summary` 与 `applies_to`，按当前文件和作用域筛选；命中后才加载规则全文。
- 只引用 `artifacts/published/<rule-id>/<version>/rule.yaml` 的固定版本及 `content_hash`。`staged/` 供评审，`generated/` 不得作为生效规则，`latest` 只供发现。
- `artifacts/` 是本地 Git 忽略目录；新工作区须从已确认的完整备份恢复并校验。空索引表示尚无已发布规则。

## 文档规范

- 产品 PRD：[项目 PRD 规范](.claude/standards/kset-framework-prd.md)
- 界面设计：[项目 UI/UX 规范](.claude/standards/kset-framework-ui-ux.md)
- 需求、技术文档与功能状态：[项目文档规范](.claude/standards/kset-framework-requirements-designer/references/project-documentation.md)

## 规范

项目规则默认适用；按当前任务范围读取对应规范，无需显式点名。下列条目是按需导航，不要求全量加载，也不构成提交、推送、测试或部署授权。


- 需求与现有逻辑说明（处理对应任务时；其他情况默认不使用）：`kset-framework-requirements-designer` → `.claude/standards/kset-framework-requirements-designer/`
- 页面文件关联：`kset-framework-file-relations` → `.claude/standards/kset-framework-file-relations/`
- 研发：`kset-framework-coder` → `.claude/standards/kset-framework-coder/`
- 修复：`kset-framework-fixer` → `.claude/standards/kset-framework-fixer/`
- 测试：`kset-framework-tester` → `.claude/standards/kset-framework-tester/`
- 环境与运行数据及部署：`kset-framework-environment` → `.claude/standards/kset-framework-environment/`
- 接口文档：`kset-framework-api-doc` → `.claude/standards/kset-framework-api-doc/`
- 需求讨论：`kset-framework-requirements-discussion` → `.claude/standards/kset-framework-requirements-discussion/`
- 项目交付规范配置：`kset-framework-delivery-config` → `.claude/standards/kset-framework-delivery-config/`
- 交付反馈：`kset-framework-delivery-feedback` → `.claude/standards/kset-framework-delivery-feedback/`
- 静态交付技术规范：`kset-framework-technical-spec` → `.claude/standards/kset-framework-technical-spec/`
- UI/UX 规范：`kset-framework-ui-ux-spec` → `.claude/standards/kset-framework-ui-ux-spec/`
- 产品规范：`kset-framework-product-spec` → `.claude/standards/kset-framework-product-spec/`
- 需求场景梳理：`kset-framework-scenario-analysis` → `.claude/standards/kset-framework-scenario-analysis/`
- Git 协作：`kset-framework-git-commit` → `.claude/standards/kset-framework-git-commit/`

## 多环境

| 环境 | 规范路径 | 索引 |
|------|----------|------|
| Claude | `.claude/standards/` | `CLAUDE.md` |
| Kimi | `.claude/standards/` | `AGENTS.md` |
| Codex | `.claude/standards/` | `AGENTS.md` |
| Cursor | `.claude/standards/` | `.cursor/rules/kset-framework-project-rules.mdc` |

## 使用指引

- 实现代码 → `kset-framework-coder`
- 修复 → `kset-framework-fixer`
- 测试设计与获授权的测试执行 → `kset-framework-tester`
- 环境连接、数据与日志定位及指定服务部署 → `kset-framework-environment`
- 项目内规范/API/DDD 设计 → `kset-framework-coder`，并将约定写入 `project-spec.md`
- 成套文档交付：先用 `kset-framework-delivery-config` 核对项目配置，再依次使用 `kset-framework-scenario-analysis`、`kset-framework-product-spec`、`kset-framework-ui-ux-spec`、`kset-framework-technical-spec`；静态前端由 `kset-framework-coder` 制作，反馈由 `kset-framework-delivery-feedback` 处理。

## 现有逻辑说明

- 生成前端页面文件关联报告 → `kset-framework-file-relations`

## Kset 全局规范接入

本项目初始化与已安装 Kset 全局技能的任务映射见 [项目主索引](.claude/AGENTS.md#kset-全局规范接入)。
