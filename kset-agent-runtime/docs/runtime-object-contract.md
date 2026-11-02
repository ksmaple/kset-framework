# Agent Runtime 对象与字段契约

## 术语

- **调用**：一次 Core `run` 或 `resume` 的活动执行；依据 `kset-agent-core/docs/core-object-contract.md` 的调用与快照契约。
- **业务上下文**：业务侧在调用时提供的当前身份与业务事实，不是授权结果；依据 `project-spec.md` 的 Agent Runtime 模块约定。
- **可见工具**：本次提供给 Agent 的工具目录，不代表执行许可；依据 `project-spec.md` 的 Agent Runtime 模块约定。

## 对象与字段

| 对象 | 字段 | 类型 | 空值与范围 | 所有者及用途 |
| --- | --- | --- | --- | --- |
| `AgentBusinessContext` | `principalId` | `String` | 必填、非空白；单次调用 | 业务侧提供的可信主体标识；工具执行时应再次核验 |
| `AgentBusinessContext` | `tenantId` | `String` | 可空；单次调用 | 业务侧租户标识，适用时用于资源隔离 |
| `AgentBusinessContext` | `roles` | `Set<String>` | 缺省空集合；元素非空白 | 当前角色标识快照，供业务判断，不是永久授权 |
| `AgentBusinessContext` | `attributes` | `Map<String,Object>` | 缺省空 Map；顶层无 null 值 | 业务侧自定义上下文，如提交内容；结构化容器只读快照，不透明 DTO 由业务保证不可变 |
| `AgentSkill` | `skillId` | `String` | 必填、非空白；Builder 内唯一 | 可复用工具集合身份，由调用方显式选择 |
| `AgentSkill` | `toolSource` | `AgentToolSource` | 必填 | 选中时按本次业务上下文生成工具 |
| `AgentToolSource` | `tools(context)` | `List<AgentTool>` | 非 null；元素非 null | 组装本次可见工具；跨源工具名必须唯一 |
| `AgentRuntime.Builder` | `tools(registry)` | `AgentToolRegistry` | 必填、非 null | 适配宿主现有的 Core 工具注册表 |
| `McpAgentClient` | `listTools(context)` | `List<AgentToolDescriptor>` | 非 null；元素非 null | 业务侧返回本次 MCP 可见目录 |
| `McpAgentClient` | `call(toolName, arguments, toolContext, businessContext)` | `ToolExecutionResult` | 遵循 Core 工具结果契约 | 业务侧执行远端调用并传递身份、deadline、取消与幂等身份 |
| `AgentRuntimeContexts` | `require(request/executionContext/toolContext)` | `AgentBusinessContext` | 缺失即抛错 | 从本次 Core 请求或执行上下文读取业务事实 |
| `AgentRuntime` | `run(request, context, selectedSkillIds)` | `AgentResult` | `context` 必填；Skill ID 列表非 null | 固定工具目录、组装 Kernel、执行新任务 |
| `AgentRuntime` | `resume(request, snapshot, input, context, selectedSkillIds)` | `AgentResult` | `context` 必填；其余遵循 Core 恢复契约 | 重新取得当前目录并调用 Core 恢复 |
| `AgentRuntime.Builder` | `modelFactory(context)` | `AgentModel` | 可选；结果非 null | 为每次调用选择当前模型，缺省使用 Builder 模型 |
| `AgentRuntime.Builder` | `strategyFactory(tools)` | `AgentReasoningStrategy` | 可选；结果非 null | 按本次工具目录创建策略，缺省使用 Core ReAct |

Runtime 将 `AgentBusinessContext` 放入专用 `AgentRequest.attributes` 键，Core 透传至 `AgentExecutionContext.attributes`；该键不进入 `AgentRunSnapshot`，不属于 `agent.*` 或 `react.*` 控制属性。默认 ReAct 不将其投影到 Prompt。它可能包含敏感内容，宿主日志、生命周期上报与错误消息不得直接打印请求属性。

业务侧负责认证上下文来源、每次工具执行的真实权限判断、MCP 凭据、工具副作用幂等与未知结果核验。Runtime 只处理组装和透传；Core 只处理循环与恢复语义。
