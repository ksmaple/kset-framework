---
type: standard
name: kset-framework-technical-spec
description: "为 kset-framework 的静态前端交付编写本地实现与验收契约；按任务范围命中时读取并适用本规范。"
---

# kset-framework 静态交付技术规范

> project-init 生成。
>
> 项目技术事实见 [project-spec](../../../docs/overview/design/project-spec.md)，差异见 [文档交付配置](../kset-framework-delivery-config/references/document-delivery-config.json)。

## 触发条件

**启用**：当前任务命中规范 `kset-framework-technical-spec`，需要为文档驱动交付制定静态前端实现契约。
**不启用**：真实业务系统的跨模块技术设计由 `kset-framework-technical-designer`（存在时）处理，源码修改由 `kset-framework-coder` 处理。

## 核心规则

R000: 本规范仅适用于（proj=kset-framework）所属项目仓库及其工作树；不得引用平台 `kaka-*` 规则或规范，不覆盖或放宽项目规则。
R001: 产品规则、页面状态和本地模拟数据须可逐项映射；静态效果不得依赖服务端数据。
R002: 文档须记录适用技术栈、组件边界、资源定位、交互状态和可复现验收步骤。
R003: 安装、启动、预览或构建仅在当前项目规则与用户授权允许时执行；未执行的步骤标为未验证。
R004: 技术交付文档须按 [产物契约](../kset-framework-delivery-config/references/artifact-format.md) 以 Markdown 保存；静态效果制作前须确定项目配置中的 `output_root`，制作后在 JSON 清单登记源码或效果入口、修订号、本地数据与可定位验收证据。

## 规范名称

技术规范规范。

## 规范定位（一句话：解决什么问题）

将产品及 UI UX 契约转为可制作、可复现的静态前端技术说明。

## 输入（Schema化：字段名/类型/是否必填/说明）

| 字段名 | 类型 | 是否必填 | 说明 |
|---|---|---|---|
| `product_document` | `{rules:{id:string}[], acceptance:{id:string}[]}` | 是 | 产品行为与验收条件。 |
| `delivery_key` | `string` | 是 | 与上游文档相同的稳定交付键。 |
| `interface_document` | `{page_id:string, rule_ids:string[], states:{name:string}[]}[]` | 是 | 页面与交互状态。 |
| `asset_manifest` | `{id:string, location:string, license:string}[]` | 是 | 静态素材；允许空数组。 |
| `project_config` | `document-delivery-config.json` | 是 | 技术栈、模拟数据与本地交付约束。 |

## 输出（Schema化：字段名/类型/说明）

| 字段名 | 类型 | 说明 |
|---|---|---|
| `technical_document` | `{architecture:string, page_components:{page_id:string, components:string[]}[], state_triggers:{page_id:string, state:string, local_trigger:string}[], data_contracts:{id:string, fields:string[], source:"local_mock"\|"static_asset"}[], run_instructions:string[], acceptance_steps:string[]}` | 静态实现契约。 |
| `fixture_manifest` | `{id:string, rule_ids:string[], state_names:string[], location:string}[]` | 本地模拟数据清单。 |
| `implementation_trace` | `{rule_id:string, page_ids:string[], acceptance_step_ids:string[]}[]` | 规则到实现验收的映射。 |
| `status` | `"draft"\|"pending"\|"ready"\|"blocked"` | 文档状态。 |
| `artifact_ref` | `{delivery_key:string, type:"technical", path:string, revision:number}` | 已保存技术文档的清单引用。 |
| `static_handoff` | `{output_root:string, manifest_path:string, acceptance_step_ids:string[]}` | 静态前端制作与验收的项目内交接。 |

## 处理流程（编号步骤，每步可验证）

1. 映射产品规则、页面、组件与交互状态，核验每条规则有去向。
2. 定义本地状态触发和模拟数据，核验目标效果不依赖服务端。
3. 写明资源、适用技术栈及运行说明，核验说明与项目事实一致。
4. 编写可观察验收步骤，核验步骤与规则及页面相连。
5. 按授权范围核验并标记已验证或未验证，保存文档、更新清单并交接静态制作。

## 工作流

按上述处理流程执行；启动、预览及构建授权按当前项目规则判定。

## 适用边界（什么情况用）

用于本地静态前端效果的技术交接；允许本地模拟交互与数据。

## 禁用场景（什么情况绝对不用）

不得引入真实账户、生产数据或远程服务作为必要条件，不得擅自修改产品契约。

## 失败模式（至少3种：什么输入会导致什么错误输出，如何识别）

| 错误输入 | 错误输出 | 识别方法 |
|---|---|---|
| 页面状态未定义 | 只实现默认画面 | 对照 UI 状态与 `state_triggers`。 |
| 数据契约要求远程接口 | 本地效果无法独立运行 | 检查 `data_contracts.source` 与运行时依赖。 |
| 技术栈或运行入口未知 | 文档不可复现 | 对照项目配置与运行说明的证据。 |

## 验收标准（可度量，至少3条）

1. 产品规则到页面及验收步骤的映射覆盖率为 100%。
2. 目标状态的本地数据与触发方式覆盖率为 100%。
3. 必需服务端数据源数量为 0；未获运行授权时实际预览状态须标为未验证。

## 依赖关系（上游/下游规范）

上游：`kset-framework-product-spec`、`kset-framework-ui-ux-spec`、`kset-framework-delivery-config`；反馈回流：`kset-framework-delivery-feedback`。静态前端制作是交付执行环节，不另设规范依赖。

## 通用性约束声明（确认本规范不包含任何项目特定内容）

本体不指定框架、目录、组件、命令或数据实例；具体项目取值只在配置及本次文档中维护。

## 参考文件

[文档交付配置](../kset-framework-delivery-config/references/document-delivery-config.json) · [项目规则](../../rules/kset-framework-project-rules.md)

## Collaboration

源码实现归 `kset-framework-coder`；界面验收归 `kset-framework-ui-ux-spec`；运行授权以项目规则为准。
