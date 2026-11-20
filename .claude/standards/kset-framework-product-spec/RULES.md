---
type: standard
name: kset-framework-product-spec
description: "为 kset-framework 的静态前端交付编写可追溯产品文档；按任务范围命中时读取并适用本规范。"
---

# kset-framework 产品规范

> project-init 生成。
>
> 项目事实见 [project-spec](../kset-framework-coder/references/project-spec.md)，项目差异见 [文档交付配置](../kset-framework-delivery-config/references/document-delivery-config.json)。

## 触发条件

**启用**：当前任务命中规范 `kset-framework-product-spec`，需要将场景整理为静态前端交付的产品文档。
**不启用**：独立 App PRD 或原型反推按 [项目 PRD 规范](../../standards/kset-framework-prd.md) 处理；不负责界面与技术实现。

## 核心规则

R000: 本规范仅适用于（proj=kset-framework）所属项目仓库及其工作树；不得引用平台 `kaka-*` 规则或规范，不覆盖或放宽项目规则。
R001: 每条产品规则须关联场景 ID，并写明条件、行为和可观察结果。
R002: 每条产品规则须有可判定的验收条件，未决条件不得伪称已确认。
R003: 本文档只定义本轮静态效果应呈现的产品行为，不把模拟数据当作真实服务端事实。
R004: 产品交付文档须按 [产物契约](../kset-framework-delivery-config/references/artifact-format.md) 以 Markdown 保存；上游场景修订变化时须重核规则追踪并更新 JSON 清单证据。

## 规范名称

产品规范规范。

## 规范定位（一句话：解决什么问题）

把场景基线转为可评审并可交给 UI UX 与技术文档的产品契约。

## 输入（Schema化：字段名/类型/是否必填/说明）

| 字段名 | 类型 | 是否必填 | 说明 |
|---|---|---|---|
| `scenarios` | `{id:string, source_ids:string[], observable_result:string}[]` | 是 | 来自 `kset-framework-scenario-analysis` 的场景。 |
| `delivery_key` | `string` | 是 | 与场景文档相同的稳定交付键。 |
| `scope_decisions` | `{item:string, decision:string, reason:string}[]` | 是 | 范围判定。 |
| `open_questions` | `{id:string, question:string, blocking:boolean}[]` | 是 | 未决问题，允许空数组。 |
| `project_config` | `document-delivery-config.json` | 是 | 当前项目配置。 |
| `previous_document` | `string\|null` | 否 | 修订时的既有文档定位。 |

## 输出（Schema化：字段名/类型/说明）

| 字段名 | 类型 | 说明 |
|---|---|---|
| `product_document` | `{goal:string, scope:string[], rules:{id:string, scenario_id:string, condition:string, behavior:string, result:string}[], exceptions:string[], acceptance:{id:string, scenario_id:string, criterion:string}[]}` | 产品行为和验收条件。 |
| `trace_matrix` | `{scenario_id:string, rule_ids:string[], acceptance_ids:string[]}[]` | 场景到规则和验收的映射。 |
| `unresolved` | `{question_id:string, affected_rule_ids:string[], blocking:boolean}[]` | 未决影响。 |
| `status` | `"draft"\|"pending"\|"ready"\|"blocked"` | 文档状态。 |
| `artifact_ref` | `{delivery_key:string, type:"product", path:string, revision:number}` | 已保存产品文档的清单引用。 |

## 处理流程（编号步骤，每步可验证）

1. 将纳入场景映射为产品规则，核验场景无遗漏。
2. 写明主流程、异常及边界行为，核验规则具备条件、行为和结果。
3. 为规则编写验收条件，核验条件可回溯场景。
4. 对照未决和既有文档，核验无冲突结论。
5. 生成追踪矩阵、设置状态，保存文档并更新清单。

## 工作流

按上述处理流程执行；文档冻结遵循当前项目规则与已确认需求。

## 适用边界（什么情况用）

用于成套文档驱动交付的产品行为定义；独立 PRD 不因本流程自动改变格式。

## 禁用场景（什么情况绝对不用）

不得替用户决定未确认业务事实，不得规定具体组件代码或服务端接口实现。

## 失败模式（至少3种：什么输入会导致什么错误输出，如何识别）

| 错误输入 | 错误输出 | 识别方法 |
|---|---|---|
| 场景无可观察结果 | 验收条件不可判定 | 检查每个 `acceptance` 的对应结果。 |
| 未决项被忽略 | 文档作出无依据承诺 | 对照 `unresolved` 与规则正文。 |
| 旧文档规则与新场景冲突 | 同一条件有两套行为 | 按场景 ID 对照新旧规则。 |

## 验收标准（可度量，至少3条）

1. 纳入场景的规则及验收条件覆盖率均为 100%。
2. 追踪矩阵中孤立规则和验收条件数量均为 0。
3. 阻塞性未决项为 0 且规则冲突数为 0 时才可标记 `ready`。

## 依赖关系（上游/下游规范）

上游：`kset-framework-scenario-analysis`、`kset-framework-delivery-config`；下游：`kset-framework-ui-ux-spec`、`kset-framework-technical-spec`；反馈回流：`kset-framework-delivery-feedback`。

## 通用性约束声明（确认本规范不包含任何项目特定内容）

本体不预设产品名称、角色、业务规则或临时文案；项目差异只在配置层维护。

## 参考文件

[文档交付配置](../kset-framework-delivery-config/references/document-delivery-config.json) · [项目规则](../../rules/kset-framework-project-rules.md)

## Collaboration

界面表现归 `kset-framework-ui-ux-spec`，静态实现契约归 `kset-framework-technical-spec`。
