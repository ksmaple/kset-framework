---
type: standard
name: kset-framework-delivery-config
description: "为 kset-framework 的六项文档交付规范维护项目配置与本体一致性；按任务范围命中时读取并适用本规范。"
---

# kset-framework 项目交付规范配置

> project-init 生成。
>
> 可编辑配置见 [document-delivery-config.json](references/document-delivery-config.json)，Markdown 文档与 JSON 内部记录格式见 [artifact-format.md](references/artifact-format.md)，项目事实见 [project-spec](../../../docs/overview/design/project-spec.md)。

## 触发条件

**启用**：当前任务命中规范 `kset-framework-delivery-config`，需要实例化或调整当前项目的文档交付规范。
**不启用**：通用规范本体修订由平台维护者处理；单功能事实不写入此配置。

## 核心规则

R000: 本规范仅适用于（proj=kset-framework）所属项目仓库及其工作树；不得引用平台 `kaka-*` 规则或规范，不覆盖或放宽项目规则。
R001: 配置须包含技术栈、业务约束、合规要求及六项选定规范本体的版本与摘要。
R002: 项目差异只写入 `document-delivery-config.json`，不得修改六项规范正文或放宽其必填字段、禁用场景和验收底线。
R003: 每次配置修订须核对六项规范文件摘要、允许的差异键及本体强制契约；平台 init 升级时另由其 verify 校验模板一致性并保留项目上下文与差异。
R004: 四份 Markdown 交付文档与两份 JSON 内部记录的交付键、默认路径、修订号及格式遵循 [产物契约](references/artifact-format.md)；项目路径差异仅通过 `project_overrides` 的 `document_paths` 配置，静态前端位置通过 `static_delivery.output_root` 配置。

## 规范名称

当前项目规范生成规范。

## 规范定位（一句话：解决什么问题）

把六项通用本体与项目上下文组合成可频繁修改的项目内规范配置。

## 输入（Schema化：字段名/类型/是否必填/说明）

| 字段名 | 类型 | 是否必填 | 说明 |
|---|---|---|---|
| `project` | `string` | 是 | 当前项目标识。 |
| `project_context` | `{technology_stack:string[], business_constraints:string[], compliance_requirements:string[]}` | 是 | 三项均须显式填写；暂无约束时使用空数组。 |
| `selected_skill_bodies` | `Record<role, {version:string, sha256:string, generated_sha256:string}>` | 是 | 六项角色各一次；分别锁定通用模板与项目内生成文件的摘要。 |
| `project_overrides` | `{skill_id:string, key:string, value:string\|string[]\|object, evidence:string[]}[]` | 是 | 项目差异；`document_paths` 须为产物类型到含 `{delivery_key}` 的项目相对路径映射。 |
| `static_delivery` | `{data_source:"local_mock_only", asset_roots:string[], preview_command:string\|null, viewports:string[], output_root:string\|null}` | 是 | 静态效果的本地数据、素材、预览、视口及项目内源码位置。 |
| `existing_config` | `object\|null` | 否 | 更新时的已有配置。 |

## 输出（Schema化：字段名/类型/说明）

| 字段名 | 类型 | 说明 |
|---|---|---|
| `project_config` | `{schema_version:number, project:string, project_context:object, selected_skill_bodies:Record<role,{version:string,sha256:string,generated_sha256:string}>, project_overrides:object[], static_delivery:{data_source:"local_mock_only", asset_roots:string[], preview_command:string\|null, viewports:string[], output_root:string\|null}}` | 项目内可执行配置文件。 |
| `consistency_report` | `{check:string, result:"pass"\|"fail", role:string, evidence:string}[]` | 必填字段、本体版本及差异一致性检查。 |
| `change_log` | `{key:string, before:string, after:string, reason:string}[]` | 本次项目配置变更。 |
| `status` | `"draft"\|"pending"\|"ready"\|"blocked"` | 配置状态。 |
| `artifact_manifest_path` | `string` | 按产物契约解析的项目内交付清单路径。 |

## 处理流程（编号步骤，每步可验证）

1. 核验项目上下文三类字段及六项本体版本，逐一对照摘要。
2. 将有依据的项目差异写入配置，核验每项差异指向允许的参数键。
3. 检查差异未删除本体必填字段、禁用场景或验收底线。
4. 在项目内核对六个 `RULES.md` 的 SHA-256 与 `generated_sha256`，并检查差异未覆盖强制契约；平台升级时另执行 init 的 verify。
5. 核对四份 Markdown 文档与两份 JSON 内部记录路径无越界或重名、静态效果制作前已填写 `output_root`；检查失败时标为 `blocked`，通过后输出变更记录、清单路径与配置状态。

## 工作流

按上述处理流程执行；本体版本升级通过平台 init 的 `--update` 完成。

## 适用边界（什么情况用）

用于项目首次配置、项目上下文变化、L1 补漏及未通过 L2 提案的项目级承接。

## 禁用场景（什么情况绝对不用）

不得用配置覆盖本体强制契约，不得把项目事实写回通用本体。

## 失败模式（至少3种：什么输入会导致什么错误输出，如何识别）

| 错误输入 | 错误输出 | 识别方法 |
|---|---|---|
| 缺项目上下文三类字段 | 规范遗漏约束 | 校验三个键均存在且为数组。 |
| 本体版本或摘要缺项 | 绑定错误规范快照 | 核验六项角色与摘要。 |
| 差异试图覆盖强制契约 | 配置与本体矛盾 | 一致性校验应失败。 |

## 验收标准（可度量，至少3条）

1. 六项版本与摘要覆盖率、匹配率均为 100%。
2. 上下文字段与差异依据完整率均为 100%。
3. 本体一致性检查通过率为 100%，冲突项为 0；配置修改引起的本体正文变更数为 0。

## 依赖关系（上游/下游规范）

上游：六项规范本体版本定义及 `kset-framework-delivery-feedback` 的 L1 回流；下游：`kset-framework-scenario-analysis`、`kset-framework-product-spec`、`kset-framework-ui-ux-spec`、`kset-framework-technical-spec`、`kset-framework-delivery-feedback`。

## 通用性约束声明（确认本规范不包含任何项目特定内容）

本体仅定义实例化与一致性机制；具体技术栈、业务约束、合规要求和术语只写入项目配置。

## 参考文件

[document-delivery-config.json](references/document-delivery-config.json) · [artifact-format.md](references/artifact-format.md) · [项目规则](../../rules/kset-framework-project-rules.md)

## Collaboration

反馈分级由 `kset-framework-delivery-feedback` 负责；其他五项规范只消费已校验配置。
