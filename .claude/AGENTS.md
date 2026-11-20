# kset-framework AI 协作配置

> 本文件是项目 AI 协作主索引。项目规范与 project-spec 维护在 `.claude/standards/`，项目规则维护在 `.claude/rules/`；适用的 Kset 全局技能按下文接入。

## 规则

- 项目规则：[kset-framework-project-rules.md](rules/kset-framework-project-rules.md)
- 项目事实：[project-spec.md](standards/kset-framework-coder/references/project-spec.md)
- Git 工作流：[docs/git-workflow.md](../docs/git-workflow.md)。直接在主工作树 `main` 修改、提交并推送；不创建任务分支或 worktree。

## AI 规则产物

- 先读项目根 `artifacts/INDEX.json` 的摘要与适用路径，按当前文件和作用域筛选；命中后才加载全文。
- 只引用 `published/<rule-id>/<version>/rule.yaml` 的固定版本及内容 hash。`staged/` 供评审，`generated/` 不得作为生效规则，`latest` 只供发现。
- 项目根 `artifacts/` 被本地 Git 忽略；新工作区从完整备份恢复并校验。空索引表示尚无已发布规则。

## 文档规范

- 产品 PRD：[项目 PRD 规范](standards/kset-framework-prd.md)
- 界面设计：[项目 UI/UX 规范](standards/kset-framework-ui-ux.md)
- 需求、技术文档与功能状态：[项目文档规范](standards/kset-framework-requirements-designer/references/project-documentation.md)

## 规范

项目规则默认适用；按当前任务范围读取对应规范，无需显式点名。下列条目是按需导航，不要求全量加载，也不构成提交、推送、测试或部署授权。


- 需求与现有逻辑说明（处理对应任务时；其他情况默认不使用）：`kset-framework-requirements-designer` → `standards/kset-framework-requirements-designer/`
- 页面文件关联：`kset-framework-file-relations` → `standards/kset-framework-file-relations/`
- 研发：`kset-framework-coder` → `standards/kset-framework-coder/`
- 修复：`kset-framework-fixer` → `standards/kset-framework-fixer/`
- 测试：`kset-framework-tester` → `standards/kset-framework-tester/`
- 环境与运行数据及部署：`kset-framework-environment` → `standards/kset-framework-environment/`
- 接口文档：`kset-framework-api-doc` → `standards/kset-framework-api-doc/`
- 需求讨论：`kset-framework-requirements-discussion` → `standards/kset-framework-requirements-discussion/`
- 项目交付规范配置：`kset-framework-delivery-config` → `standards/kset-framework-delivery-config/`
- 交付反馈：`kset-framework-delivery-feedback` → `standards/kset-framework-delivery-feedback/`
- 静态交付技术规范：`kset-framework-technical-spec` → `standards/kset-framework-technical-spec/`
- UI/UX 规范：`kset-framework-ui-ux-spec` → `standards/kset-framework-ui-ux-spec/`
- 产品规范：`kset-framework-product-spec` → `standards/kset-framework-product-spec/`
- 需求场景梳理：`kset-framework-scenario-analysis` → `standards/kset-framework-scenario-analysis/`
- Git 协作：`kset-framework-git-commit` → `standards/kset-framework-git-commit/`

## 四平台

| 环境 | 规范路径 | 索引 |
|------|----------|------|
| Claude | `.claude/standards/` | `CLAUDE.md` |
| Kimi | `.claude/standards/` | `AGENTS.md` |
| Codex | `.claude/standards/` | `AGENTS.md` |
| Cursor | `.claude/standards/` | `.cursor/rules/kset-framework-project-rules.mdc` |

各平台直接读取项目内的 `.claude/standards/`，不依赖本地目录链接。

## Kset 全局规范接入

已安装的全局技能按任务调用；项目规则优先，项目规范补充本仓流程，project-spec 只记录有来源的事实。技能正文由 Kset 全局安装维护，本仓不复制。

| 任务 | 全局技能 | 项目补充 |
| --- | --- | --- |
| 项目 AI 协作初始化 | `kset-util-project-init` | 本索引、项目规则与 project-spec |
| 研发与技术设计 | `kset-project-coder`、`kset-coder-designer` | `kset-framework-coder` |
| 修复与测试 | `kset-project-fixer`、`kset-project-tester` | `kset-framework-fixer`、`kset-framework-tester` |
| 需求、接口和页面关联 | `kset-project-requirements-designer`、`kset-project-api-doc`、`kset-project-file-relations` | 同名 `kset-framework-*` 规范 |
| 环境与文档交付 | `kset-project-environment`、`kset-project-document-delivery` | 同名 `kset-framework-*` 规范及交付配置 |
| Git 协作 | `kset-util-git-commit` | `docs/git-workflow.md` 与 `kset-framework-git-commit` |

## 使用指引
- 成套文档交付：先用 `kset-framework-delivery-config` 核对项目配置，再依次使用 `kset-framework-scenario-analysis`、`kset-framework-product-spec`、`kset-framework-ui-ux-spec`、`kset-framework-technical-spec`；静态前端由 `kset-framework-coder` 制作，反馈由 `kset-framework-delivery-feedback` 处理。
