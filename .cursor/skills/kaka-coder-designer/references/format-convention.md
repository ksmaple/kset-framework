# 平台文档书写约定

> 技能、规则、研发域规范三类文档的编号格式。新建或修订任一类文档前须读本文件。
> **全局强制**：`.claude/rules/kaka-project-rules.md` **R021–R029**（与本文一致，规则文件优先作 AI 默认加载入口）。

## 一、技能（`.claude/skills/**/SKILL.md` 与技能 `references/`）

> 全局规则：`kaka-project-rules` R021、R025、R026、R028、R029。

SKL001: 条文格式固定为 `R{NNN}: 描述`（三位数字，如 R001、R012）
SKL002: `R000` / `R000b` 等可表示门禁或分支规则，仍须符合 `R` + 编号 + 冒号 + 描述
SKL003: `SKILL.md` 的 **核心规则** 节只写 R 编号条文，不写长段落教程
SKL004: 技能 `references/*.md` 细则同样用 `R001:` 一行一条（init 规范、模板、命名约定等）
SKL005: 工作流可用简短代码块列步骤；**约束性条文**不得放进代码块
SKL006: 技能文件内编号默认从 R001 递增；新增使用下一可用号；同一文件内禁止重复编号，是否允许保留历史空洞由所属模板或项目约定决定
SKL007: 一条一约束，单行完整短句，说明做什么或禁止什么
SKL008: 引用他处规范写「见 {文件} R{NNN}」或「见 {domain}-spec N{NNN}」，禁止复制全文
SKL009: `SKILL.md` 建议 ≤200 行；细则进 `references/`
SKL010: 禁止在技能正文中用无序列表代替 R 编号表达强制约束（表格、索引、协作路由除外）

## 二、项目规则（`.claude/rules/kaka-*.md`）

> 全局规则：`kaka-project-rules` R022–R024、R027–R029。

RUL001: 条文格式固定为 `R{NNN}: 描述`，与技能 R 编号写法相同
RUL002: 同一仓库内 `.claude/rules/*.md` 的 `R{NNN}` **全局唯一**，禁止跨文件重复编号
RUL003: 允许字母后缀区分子规则，如 R007a、R007d；仍视为同一主编号族
RUL003a: 项目规则主编号默认使用未占用三位数；为保持引用稳定，删除后允许保留历史空洞，但禁止复用旧编号
RUL004: 一条一约束，单行祈使句；禁止一条内多个「须/禁止」
RUL005: 规则只写**项目约束**（协作流程、目录、编码纪律等），不写 API/DDD/SQL 方法论全文
RUL006: 方法论细则写入 `kaka-coder-designer/references/*-spec.md` 或 `project-spec.md`
RUL007: 新增/修改前须前置守卫，避免与现有 R 编号或语义重复（见 pre-generation-guard-spec）
RUL008: 声明引用时须带规则文件名，如 `kaka-project-rules R007`，避免与各技能内 R007 混读

## 三、研发域规范（`kaka-coder-designer/references/*-spec.md`）

FMT001: 条文格式为 `PREFIXNNN: 描述`（如 N001、A012、S020）
FMT002: 前缀按域固定：naming→N，ddd→D，api→A，sql→S，frontend→F，conversion→V，event→E，cache→K，orchestration→O，log→L，fix→X，engineering→G，coding→C
FMT003: 编号连续、稳定；新增用下一可用号，禁止插队改义
FMT004: 描述用完整短句；禁止代码块、伪代码、多语言示例、JSON/YAML 样例
FMT005: 禁止在 spec 内重复 naming 全文；跨域引用写「见 naming 域 Nxxx」
FMT006: 文件名 `{domain}-spec.md`，domain 与 init-skills-manifest 域目录一致
FMT007: 域 spec 不使用 `R001` 前缀，避免与技能/规则 R 编号混淆

## 四、对照

| 文档类型 | 路径示例 | 编号格式 | 权威维护 |
|----------|----------|----------|----------|
| 技能 | `kaka-utils-*/SKILL.md` | R001: 描述 | kaka-utils-skill-creator |
| 技能细则 | `kaka-*/references/*.md` | R001: 描述 | 所属技能 |
| 项目规则 | `.claude/rules/kaka-*.md` | R001: 描述 | kaka-utils-rule-creator |
| 研发域 | `kaka-coder-designer/references/*-spec.md` | N001 / A001 … | kaka-coder-designer |
| 业务差异 | `{proj}-coder/references/project-spec.md` | 建议 R 或项目约定节 | project-init |

## 五、禁止

FMT900: 混用「- 须…」「1. 禁止…」列表充当正式编号条文（技能核心规则、规则文件、域 spec 均适用）
FMT901: 技能/规则文件内用域前缀 N001、A001 写方法论（应放在 *-spec.md）
FMT902: 域 spec 内用 R001 写研发规范（应改用域前缀）
