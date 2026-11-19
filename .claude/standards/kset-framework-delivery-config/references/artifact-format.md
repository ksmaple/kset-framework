# 文档交付产物契约

> project-init 生成。本文件定义四份 Markdown 交付文档及两份 JSON 内部记录；实际项目路径差异只写入 `document-delivery-config.json`。

R001: 同一交付使用稳定的 `delivery_key`，格式为小写 kebab-case 单个路径片段；不同交付不得复用同一键，修订原交付时保持原键。
R002: 场景、产品、UI/UX 和技术正文统一以 Markdown 交付；`feedback.json` 和 `manifest.json` 仅用于内部事件与追踪，不作为面向人阅读的交付文档。六类文件默认位于 `.claude/design/document-delivery/{delivery_key}/`，只有实际生成时才创建文件，不预建空目录。
R003: 项目配置可用 `project_overrides` 中 `skill_id=delivery-config`、`key=document_paths` 的对象覆盖下表单个文件路径；键只能是下表类型，值须为含 `{delivery_key}` 的项目内相对路径，四份文档保留 `.md`、两份内部记录保留 `.json`，不能含 `..` 或与其他产物重名。
R003a: 每份 Markdown 文档首个非空行须为带非空标题的一级标题（`# 标题`），标题后至少有一行非空正文；格式检查不代替内容与证据验收。
R004: 每个已保存的核心交付产物（不含清单自身）须在 `manifest.json` 登记路径、正整数修订号、来源 ID、上游产物修订、状态及验证结果；附属素材与本地数据在所属产物条目中登记。内容修改时仅递增该产物修订号并递增清单修订号，不重置其他产物历史。
R005: 静态前端源码位置取项目配置的 `static_delivery.output_root`，允许项目首次 init 时为 `null`；实际制作前须填写项目内相对路径，交付清单记录源码或效果入口、修订号、所用本地数据与可定位的验收证据。未运行时标 `not_run`，不得标为通过。
R006: `feedback.json` 按反馈 ID 保存事件、L0–L3 分级、受影响产物修订号、动作、L2 评审及关闭证据；同一 ID 更新状态，保留已有事件，不用新反馈覆盖旧记录。关闭前须有复验证据。
R007: `manifest.json` 只登记当前产物修订；反馈事件引用当时的产物修订。前序文档变化时重新核对下游追踪与验收，不能沿用失效的通过证据。
R008: 本地协作文档、清单和反馈文件不因规范生成而自动忽略或取消跟踪；用户指定为正式交付的文件及静态前端源码、资源遵循项目版本管理，只有临时产物按项目规则排除。
R009: 静态前端标为 `ready` 前须按产品验收 ID 和项目配置视口人工核对关键交互及适用状态，并在清单记录核对人、时间、覆盖项和证据；自动检查只能验证记录完整，不能替代实际运行与体验判断。

## 默认文件

| 类型 | 默认路径 | 用途与格式 |
|---|---|---|
| `scenarios` | `.claude/design/document-delivery/{delivery_key}/scenarios.md` | 场景交付文档，Markdown |
| `product` | `.claude/design/document-delivery/{delivery_key}/product.md` | 产品交付文档，Markdown |
| `ui_ux` | `.claude/design/document-delivery/{delivery_key}/ui-ux.md` | UI/UX 交付文档，Markdown；素材另按项目路径定位 |
| `technical` | `.claude/design/document-delivery/{delivery_key}/technical.md` | 技术交付文档，Markdown |
| `feedback` | `.claude/design/document-delivery/{delivery_key}/feedback.json` | 内部反馈事件记录，JSON |
| `manifest` | `.claude/design/document-delivery/{delivery_key}/manifest.json` | 内部追踪清单，JSON |

## 清单 Schema

`manifest.json` 为 `{schema_version:1, delivery_key:string, manifest_revision:number, artifacts:Artifact[]}`；`manifest_revision` 从 1 开始，每次清单变更加 1。`Artifact` 为 `{type:"scenarios"|"product"|"ui_ux"|"technical"|"static_frontend"|"feedback", path:string, revision:number, status:"draft"|"pending"|"ready"|"blocked", source_ids:string[], depends_on:{type:"scenarios"|"product"|"ui_ux"|"technical"|"static_frontend"|"feedback", revision:number}[], assets:{path:string, purpose:string, license:string}[], local_data:{id:string, path:string}[], verification:{result:"pass"|"fail"|"not_run", evidence:string[]}, manual_review:{reviewer:string, checked_at:string, acceptance_ids:string[], viewports:string[], evidence:string[]}|null}`。无上游、附属素材或本地数据时对应数组为空；非静态产物的 `manual_review` 为 `null`。每个 `type` 在同一清单中最多一条，`revision` 从 1 开始；`ready` 须有可定位的 `pass` 证据。`product` 须依赖 `scenarios`，`ui_ux` 依赖 `product`，`technical` 依赖 `product` 与 `ui_ux`，`static_frontend` 依赖 `technical` 与 `ui_ux`。

`feedback.json` 为 `{schema_version:1, delivery_key:string, events:FeedbackEvent[]}`；`FeedbackEvent` 为 `{id:string, description:string, expected:string, actual:string, level:"L0"|"L1"|"L2"|"L3", status:"open"|"working"|"verify"|"closed"|"blocked", artifact_refs:{type:"scenarios"|"product"|"ui_ux"|"technical"|"static_frontend"|"feedback", revision:number}[], evidence:{source_id:string, location:string}[], actions:{target:"artifact"|"project_config"|"body_proposal"|"system_review", change:string, verification:string}[], l2_review:{cross_project:boolean, project_scope_ids:string[], de_businessized:boolean, metric_benefit:boolean, metric_evidence:{metric:string, baseline:number, result:number}|null, decision:"propose"|"downgrade", reason:string}|null, closure_evidence:{source_id:string, location:string}[]}`。`id` 在同一交付中唯一；非 L2 的 `l2_review` 为 `null`，L2 通过时须有至少两个独立 `project_scope_ids` 和指标前后值，已关闭事件的 `closure_evidence` 非空。

## 验收

1. 每个已保存的核心交付产物（不含清单自身）在清单中的路径和修订号登记率为 100%，附属素材路径与许可登记率为 100%，同一交付键内重复类型与重复反馈 ID 均为 0。
2. 静态前端 `ready` 时项目路径、本地数据文件、产品验收 ID、配置视口及人工核对证据完整率为 100%；服务端必需依赖数为 0。
3. 已关闭反馈的受影响修订号和复验证据完整率为 100%；前序文档变更后下游失效证据数为 0。
4. 四类已保存交付文档的 Markdown 一级标题及非空正文合规率为 100%；反馈与清单的 JSON Schema 校验通过率为 100%。
