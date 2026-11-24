---
type: standard
name: kset-framework-ui-ux-spec
description: "为 kset-framework 的静态前端交付编写界面交互与素材文档；按任务范围命中时读取并适用本规范。"
---

# kset-framework UI/UX 规范

> project-init 生成。
>
> 项目界面事实见 [project-spec](../../../docs/overview/design/project-spec.md)，差异见 [文档交付配置](../kset-framework-delivery-config/references/document-delivery-config.json)。

## 触发条件

**启用**：当前任务命中规范 `kset-framework-ui-ux-spec`，需要为已确认产品规则编写界面、交互与素材契约。
**不启用**：直接设计、实施或评审现有界面时按 [项目 UI/UX 规范](../../standards/kset-framework-ui-ux.md)；不负责产品规则和源码。

## 核心规则

R000: 本规范仅适用于（proj=kset-framework）所属项目仓库及其工作树；不得引用平台 `kaka-*` 规则或规范，不覆盖或放宽项目规则。
R001: 每个页面须关联产品规则 ID，并定义操作、反馈、状态、适配与无障碍要求。
R002: 素材须登记用途、定位和使用许可；无法确认许可的素材不得标为可交付。
R003: 界面文档不得改变产品业务规则，也不得以视觉稿推断未确认的业务条件。
R004: UI/UX 交付文档须按 [产物契约](../kset-framework-delivery-config/references/artifact-format.md) 以 Markdown 保存，素材位置和许可须随修订在 JSON 清单中保持可追溯。

## 规范名称

UI/UX 规范规范。

## 规范定位（一句话：解决什么问题）

把产品行为转为可制作、可核验的界面结构、交互状态和素材契约。

## 输入（Schema化：字段名/类型/是否必填/说明）

| 字段名 | 类型 | 是否必填 | 说明 |
|---|---|---|---|
| `product_document` | `{rules:{id:string}[], acceptance:{id:string}[]}` | 是 | 已标注状态的产品依据。 |
| `delivery_key` | `string` | 是 | 与产品文档相同的稳定交付键。 |
| `trace_matrix` | `{scenario_id:string, rule_ids:string[]}[]` | 是 | 规则来源映射。 |
| `project_config` | `document-delivery-config.json` | 是 | 设备及视觉约束。 |
| `existing_assets` | `{id:string, type:string, location:string, license:string}[]` | 是 | 无素材时填空数组。 |

## 输出（Schema化：字段名/类型/说明）

| 字段名 | 类型 | 说明 |
|---|---|---|
| `interface_document` | `{page_id:string, rule_ids:string[], structure:string[], interactions:{action:string, trigger:string, feedback:string}[], states:{name:string, appearance:string}[], responsive_rules:string[], accessibility_rules:string[]}[]` | 页面与交互说明。 |
| `asset_manifest` | `{id:string, page_ids:string[], type:string, location:string, license:string}[]` | 可定位素材。 |
| `interface_trace` | `{rule_id:string, page_ids:string[], state_names:string[]}[]` | 产品规则到页面状态的映射。 |
| `status` | `"draft"\|"pending"\|"ready"\|"blocked"` | 文档状态。 |
| `artifact_ref` | `{delivery_key:string, type:"ui_ux", path:string, revision:number}` | 已保存界面文档的清单引用。 |

## 处理流程（编号步骤，每步可验证）

1. 将产品规则映射到页面或明确的非页面交互，核验规则去向完整。
2. 定义导航、信息层级、操作和反馈，核验关键路径可走通。
3. 列出默认、加载、空、错误、成功及适用边界状态，核验结果有表现。
4. 登记素材、响应式和无障碍要求，核验素材可定位且许可明确。
5. 生成界面追踪矩阵、设置状态，保存文档并更新清单。

## 工作流

按上述处理流程执行；实际视口验证受当前项目规则的运行授权约束。

## 适用边界（什么情况用）

用于成套文档驱动交付的界面和素材说明；可包含线框或设计稿的定位。

## 禁用场景（什么情况绝对不用）

不得改写产品规则，不得要求远程服务端数据作为静态效果的验收前提。

## 失败模式（至少3种：什么输入会导致什么错误输出，如何识别）

| 错误输入 | 错误输出 | 识别方法 |
|---|---|---|
| 产品规则缺结果状态 | 缺空态或错误反馈 | 对照规则结果与 `states`。 |
| 素材无定位或许可 | 产物无法交付 | 检查 `asset_manifest` 的必填字段。 |
| 只给单一视口 | 小屏内容溢出 | 对照配置中的目标视口逐一核验。 |

## 验收标准（可度量，至少3条）

1. 需要界面呈现的产品规则映射覆盖率为 100%。
2. 每页默认态和适用异常态覆盖率为 100%。
3. 素材定位及许可完整率为 100%；配置要求的适配与无障碍项检查通过率为 100%。

## 依赖关系（上游/下游规范）

上游：`kset-framework-product-spec`、`kset-framework-delivery-config`；下游：`kset-framework-technical-spec`；反馈回流：`kset-framework-delivery-feedback`。

## 通用性约束声明（确认本规范不包含任何项目特定内容）

本体不内置品牌、配色、设备、图片或文案；这些差异只存在于项目配置和产物中。

## 参考文件

[文档交付配置](../kset-framework-delivery-config/references/document-delivery-config.json) · [项目规则](../../rules/kset-framework-project-rules.md)

## Collaboration

产品规则归 `kset-framework-product-spec`；静态实现契约归 `kset-framework-technical-spec`。
