---
type: standard
name: kset-framework-scenario-analysis
description: "为 kset-framework 的文档驱动交付梳理可追溯需求场景；按任务范围命中时读取并适用本规范。"
---

# kset-framework 需求场景梳理

> project-init 生成。
>
> 项目事实见 [project-spec](../kset-framework-coder/references/project-spec.md)，本流程差异见 [文档交付配置](../kset-framework-delivery-config/references/document-delivery-config.json)。

## 触发条件

**启用**：当前任务命中规范 `kset-framework-scenario-analysis`，需要为静态前端文档交付整理需求场景。
**不启用**：仅描述存量源码逻辑时使用 `kset-framework-requirements-designer`；仅修改产品文档时使用 `kset-framework-product-spec`。

## 核心规则

R000: 本规范仅适用于（proj=kset-framework）所属项目仓库及其工作树；不得引用平台 `kaka-*` 规则或规范，不覆盖或放宽项目规则。
R001: 每个场景须有唯一 ID、来源定位、参与者、触发条件、主流程、异常流程及可观察结果。
R002: 缺失或冲突的业务条件须登记为待确认项，不得推测为已确认事实。
R003: 场景仅定义用户结果与范围，产品规则、界面设计和技术实现分别交由下游规范。
R004: 场景交付文档须按 [产物契约](../kset-framework-delivery-config/references/artifact-format.md) 以 Markdown 保存并使用稳定交付键，路径、修订号、来源与核验结果登记到 JSON 清单。

## 规范名称

梳理需求场景规范。

## 规范定位（一句话：解决什么问题）

将零散需求整理为三类文档可共同引用的场景基线。

## 输入（Schema化：字段名/类型/是否必填/说明）

| 字段名 | 类型 | 是否必填 | 说明 |
|---|---|---|---|
| `goal` | `string` | 是 | 本轮交付目标。 |
| `delivery_key` | `string` | 是 | 本轮交付的稳定小写 kebab-case 文件键。 |
| `sources` | `{id:string, location:string, text:string}[]` | 是 | 至少一项可定位原始来源。 |
| `scope` | `{included:string[], excluded:string[]}` | 是 | 本轮范围，允许显式空数组。 |
| `project_config` | `document-delivery-config.json` | 是 | 当前项目配置及版本锁定。 |

## 输出（Schema化：字段名/类型/说明）

| 字段名 | 类型 | 说明 |
|---|---|---|
| `scenarios` | `{id:string, actor:string, trigger:string, preconditions:string[], main_flow:string[], exception_flow:string[], observable_result:string, source_ids:string[]}[]` | 场景清单。 |
| `scope_decisions` | `{item:string, decision:"included"\|"excluded"\|"pending", reason:string}[]` | 范围判定。 |
| `open_questions` | `{id:string, question:string, scenario_ids:string[], blocking:boolean}[]` | 未确认条件。 |
| `status` | `"draft"\|"pending"\|"ready"\|"blocked"` | 交付状态。 |
| `artifact_ref` | `{delivery_key:string, type:"scenarios", path:string, revision:number}` | 已保存场景文档的清单引用。 |

## 处理流程（编号步骤，每步可验证）

1. 登记来源及定位，逐项核验来源可回查。
2. 提取参与者、触发、条件、动作和结果，核验每个场景关联来源。
3. 拆分主流程、异常流程与范围，核验结果可由用户观察。
4. 登记冲突和缺失条件，核验受影响场景已标记。
5. 按验收标准设置状态，保存文档并更新清单，再交给 `kset-framework-product-spec`。

## 工作流

按上述处理流程执行；阻塞与完成门禁遵循当前项目规则。

## 适用边界（什么情况用）

用于静态前端交付进入产品文档之前的场景梳理，以及反馈导致的场景变更。

## 禁用场景（什么情况绝对不用）

不得代替用户确认业务规则，不得编造服务端数据或直接决定界面与代码方案。

## 失败模式（至少3种：什么输入会导致什么错误输出，如何识别）

| 错误输入 | 错误输出 | 识别方法 |
|---|---|---|
| 来源缺定位 | 场景不可追溯 | 检查每个 `source_ids` 能否定位原文。 |
| 来源条件冲突 | 同一场景条件互斥 | 按场景 ID 对照来源并检查 `open_questions`。 |
| 目标只有页面或组件名称 | 场景缺用户结果 | 检查 `observable_result` 是否可观察。 |

## 验收标准（可度量，至少3条）

1. 场景 ID 唯一率和来源定位有效率均为 100%。
2. 参与者、触发、主流程和结果的字段完整率为 100%。
3. 已发现冲突登记率为 100%；阻塞项未关闭时 `status` 不得为 `ready`。

## 依赖关系（上游/下游规范）

上游：`kset-framework-delivery-config`；下游：`kset-framework-product-spec`；反馈回流：`kset-framework-delivery-feedback`。

## 通用性约束声明（确认本规范不包含任何项目特定内容）

本体仅定义场景契约；项目名称、业务事实和临时文案仅由项目配置及本次输入提供。

## 参考文件

[文档交付配置](../kset-framework-delivery-config/references/document-delivery-config.json) · [项目规则](../../rules/kset-framework-project-rules.md)

## Collaboration

存量源码行为说明归 `kset-framework-requirements-designer`；本规范产出的场景 ID 由下游文档沿用。
