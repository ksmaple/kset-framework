---
type: standard
name: kset-framework-delivery-feedback
description: "为 kset-framework 的文档与静态效果反馈执行 L0 至 L3 分级回流；按任务范围命中时读取并适用本规范。"
---

# kset-framework 交付反馈处理

> project-init 生成。
>
> 项目差异见 [文档交付配置](../kset-framework-delivery-config/references/document-delivery-config.json)。

## 触发条件

**启用**：当前任务命中规范 `kset-framework-delivery-feedback`，需要处理文档或静态前端交付反馈。
**不启用**：没有可定位反馈事件时不创建改进提案；本规范不直接修改平台通用本体。

## 核心规则

R000: 本规范仅适用于（proj=kset-framework）所属项目仓库及其工作树；不得引用平台 `kaka-*` 规则或规范，不覆盖或放宽项目规则。
R001: 每条反馈须分为 L0 当前产物修复、L1 项目配置补漏、L2 规范本体修改提案或 L3 体系评估，并记录证据与复验结果。
R002: L2 提案只有跨至少两个独立项目复现、去业务化且至少一项可测指标受益时才可提交平台本体评审。
R003: 未通过 L2 通用性评审的提案须降级为当前项目配置项，并按 `kset-framework-delivery-config` 校验不得放宽本体要求。
R004: 未经复验的反馈不得标为已关闭；项目级规范不得自行改写平台本体。
R005: 每条反馈须按 [产物契约](../kset-framework-delivery-config/references/artifact-format.md) 用唯一 ID 写入内部记录 `feedback.json`，引用受影响产物修订号并更新 `manifest.json`；后续状态变更须更新同一事件，不得覆盖其他反馈。两份 JSON 均不替代 Markdown 交付文档。

## 规范名称

交付反馈处理规范。

## 规范定位（一句话：解决什么问题）

将用户修正分流到产物、项目配置、通用本体提案或体系评估。

## 输入（Schema化：字段名/类型/是否必填/说明）

| 字段名 | 类型 | 是否必填 | 说明 |
|---|---|---|---|
| `feedback` | `{id:string, description:string, expected:string, actual:string, evidence:{source_id:string, location:string}[]}` | 是 | 至少一项可定位证据。 |
| `delivery_key` | `string` | 是 | 反馈所属交付的稳定文件键。 |
| `artifacts` | `{id:string, type:"scenarios"\|"product"\|"ui_ux"\|"technical"\|"static_frontend"\|"feedback", revision:number, location:string}[]` | 是 | 受影响产物，修订号与交付清单一致。 |
| `project_config` | `document-delivery-config.json` | 是 | 项目配置与本体版本。 |
| `cross_project_evidence` | `{project_scope_id:string, reproduction:string, metric:string, baseline:number, result:number}[]` | 是 | 无证据时填空数组。 |

## 输出（Schema化：字段名/类型/说明）

| 字段名 | 类型 | 说明 |
|---|---|---|
| `classification` | `{feedback_id:string, level:"L0"\|"L1"\|"L2"\|"L3", cause:string, artifact_ids:string[], status:"open"\|"working"\|"verify"\|"closed"\|"blocked"}` | 唯一等级及状态。 |
| `actions` | `{target:"artifact"\|"project_config"\|"body_proposal"\|"system_review", change:string, verification:string}[]` | 处理动作。 |
| `l2_review` | `{cross_project:boolean, project_scope_ids:string[], de_businessized:boolean, metric_benefit:boolean, metric_evidence:{metric:string, baseline:number, result:number}\|null, decision:"propose"\|"downgrade", reason:string}\|null` | L2 必填，其余为 `null`；通过时至少两个独立项目及指标前后值。 |
| `closure_evidence` | `{source_id:string, location:string}[]` | 关闭时的复验证据。 |
| `artifact_ref` | `{delivery_key:string, type:"feedback", path:string, revision:number}` | 持久化反馈记录的清单引用。 |

## 处理流程（编号步骤，每步可验证）

1. 登记反馈、版本和受影响产物，核验事件可复现。
2. 按最小有效范围分为 L0、L1、L2 或 L3，核验归因理由。
3. 对 L2 核验跨项目复现、去业务化和指标收益；未通过时生成 L1 配置项。
4. 将 L0 指向产物、L1 指向配置、通过的 L2 指向平台提案、L3 指向体系评估。
5. 复验处理结果，按反馈 ID 保存事件与关闭证据或阻塞原因，并更新清单。

## 工作流

按上述处理流程执行；平台本体变更由平台维护者另行评审。

## 适用边界（什么情况用）

用于场景、三类文档及静态前端效果的评审与交付后反馈。

## 禁用场景（什么情况绝对不用）

不得把单项目偏好直接合入通用本体，不得绕过 L2 评审或无证据关闭。

## 失败模式（至少3种：什么输入会导致什么错误输出，如何识别）

| 错误输入 | 错误输出 | 识别方法 |
|---|---|---|
| 反馈无实际结果或证据 | 主观意见被误作普遍缺陷 | 检查版本、复现与定位。 |
| L2 仅有单项目证据 | 项目特例进入本体 | 检查独立 `project_scope_id` 数量。 |
| L2 提案保留业务术语 | 通用本体污染 | 检查去业务化记录与配置归属。 |
| 修复未复验 | 提前关闭反馈 | 检查 `closure_evidence`。 |

## 验收标准（可度量，至少3条）

1. 反馈等级、归因、产物及状态登记完整率为 100%。
2. 通过的 L2 三项评审通过率均为 100%；未通过 L2 的项目配置转化率为 100%。
3. 已关闭反馈的复验与关闭证据完整率均为 100%。

## 依赖关系（上游/下游规范）

上游：`kset-framework-scenario-analysis`、`kset-framework-product-spec`、`kset-framework-ui-ux-spec`、`kset-framework-technical-spec`、`kset-framework-delivery-config`；L1 和未通过 L2 回流 `kset-framework-delivery-config`，通过的 L2 形成平台评审提案。

## 通用性约束声明（确认本规范不包含任何项目特定内容）

本体只定义分级与回流机制；反馈实例、项目指标和业务术语仅存在于产物或配置中。

## 参考文件

[文档交付配置](../kset-framework-delivery-config/references/document-delivery-config.json) · [项目规则](../../rules/kset-framework-project-rules.md)

## Collaboration

各产物规范负责具体修订，`kset-framework-delivery-config` 负责配置补漏；平台维护者决定本体提案是否合入。
