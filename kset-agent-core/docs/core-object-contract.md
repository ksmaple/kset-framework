# Agent Core 对象与字段契约

本表定义 Java API 语义；`agent-json:v1` 的模型输出字段另见[内置协议](protocols/agent-json-v1.md)。同名字段只在所属对象的作用域内解释，不能跨层当作控制指令。`null` 表示该阶段不适用，空集合表示适用但没有元素。公开结构化容器进入 core 时递归复制为只读值；不透明 DTO 由宿主保证不可变或按调用隔离。

## 对象职责

| 对象 | 唯一职责 |
| --- | --- |
| `AgentKernelBuilder` / `AgentLoopKernel` | 启动期组装固定扩展 / 并发执行相互隔离的同步 `run/resume` 循环。 |
| `AgentReasoningStrategy` / `AgentTurn` | 决定本轮模型输入、动作语义和策略私有状态 / 承载本轮协议与模型请求。 |
| `AgentProtocolCodec` / `AgentProtocolRegistry` | 解析一种版本化模型输出 / 管理不可覆盖的协议 ID。 |
| `AgentActionHandler` / `AgentActionRegistry` | 执行一种动作类型 / 按 `actionType` 分发；Handler 通过结果声明状态变化。 |
| `AgentModel` / `AgentTool` / `AgentToolRegistry` | 调用模型 / 执行工具 / 提供固定工具目录，均不得自行生成内核追踪身份。 |
| `AgentStopController` / `AgentStopPolicy` | 管理硬停止条件和状态出口 / 只追加宿主停止条件，不能伪造完成回答。 |
| `AgentCheckpointPort` / `AgentLifecycleListener` | 同步写入宿主快照 / 接收观测事件；观测日志不是恢复权威数据。 |
| `AgentRunIdGenerator` / `AgentCancellation` | 生成新 `agentRunId` / 表示单次活动调用的协作取消信号。 |

## 身份与计数

| 字段 | 含义与范围 | 产生者及持久化 |
| --- | --- | --- |
| `agentRunId` | 一次可恢复 Agent 任务的 ID，跨 `run/resume` 不变；Java API、快照、宿主日志和存储统一使用此名 | 请求指定或 Kernel 在新运行入口生成；进入快照 |
| `sessionId` | 宿主会话 ID；一个会话可含多个 Agent 任务，不属于内核 API | 宿主产生、保存 |
| `invocationId` | 单次活动 `run/resume` 调用 ID | Kernel 每次调用生成；只用于执行上下文和观测，不进入快照 |
| `stepId` / `parentStepId` | 可配对的逻辑步骤 ID / 父步骤 ID，覆盖 run、turn、model、decision、action、checkpoint | Kernel 每次调用生成；仅观测，不进入快照 |
| `eventId` / `eventSequence` | 一条观测事件 ID / 本次 invocation 内从 1 起递增的序号；`eventId = invocationId:eventSequence` | Kernel 生成；仅观测，不进入快照 |
| `callId` | 同一 run 内一个固定工具操作 ID；同 ID 只能复用原 `taskId/toolName/arguments` 身份 | 协议或宿主动作提供；进入操作账本和标准工具 Observation；工具幂等键为 `(agentRunId, callId)` |
| `taskId` | 仅表示计划中的一个 `PlanTask` ID；工具动作中的同名字段是其可空关联 ID | 协议提供；进入计划、工具身份和 Observation；不是宿主工作流 ID |
| `confirmationId` | 一个确认动作的 ID；宿主恢复时用请求属性 `agent.approvedConfirmationIds` 回传 | 协议提供；待确认动作进入快照 |
| `turn` | 已开始的推理轮数，新 run 为 0，进入下一轮时加 1 | Kernel 管理；进入快照与观测 |
| `actionIndex` | 当前决策中零基动作位置；非动作事件为 `NO_ACTION = -1` | Kernel 管理；仅观测 |

## 请求、执行与模型

| 对象 | 字段 | 固定含义 |
| --- | --- | --- |
| `AgentRequest` | `agentRunId/task/protocolId` | Agent 任务身份、完整任务内容、整个 run 固定的版本化控制协议。`task` 不是计划节点。 |
| `AgentRequest` | `options/attributes/cancellation` | 本次调用的容量与活动期限、宿主上下文、独立取消探针。`options` 缺省用默认值；`attributes` 不自动进入默认 ReAct Prompt，仅允许公开保留键 `agent.approvedConfirmationIds` 和 `agent.approvedToolCallIds`。缺少待处理类型对应的批准 ID 会在模型调用前拒绝。 |
| `AgentLoopOptions` | `maxTurns/protocolErrorLimit/noProgressLimit` | 单个 run 的轮次上限、连续可纠正协议错误上限、连续无进展动作上限。 |
| `AgentLoopOptions` | `invocationTimeout/maxOutputTokens/maxActionsPerDecision` | 每次活动 invocation 的时限、模型本轮输出 token 提示上限（0 表示不指定）、单个决策的动作数上限。 |
| `AgentLoopOptions` | `toolCallTimeout/toolBatchTimeout/maxToolCallsPerBatch` | 单工具时限、整个标准并发批次时限、标准批次大小上限；实际工具 deadline 不得晚于 invocation deadline。 |
| `AgentExecutionContext` | `agentRunId/invocationId/stepId/parentStepId/stepType/operation/turn` | Kernel 下传给扩展的只读追踪身份；`operation` 是本步骤操作名，不是工具幂等 ID。 |
| `AgentExecutionContext` | `executionStartedAt/issuedAt/deadline/cancellation/attributes` | invocation 开始、本上下文创建、invocation 截止、取消探针、请求属性的只读副本；这些不代表任务整个生命周期。 |
| `AgentTurn` | `protocolId/modelRequest` | 策略为本轮选定的已注册协议及模型请求；恢复期间不得切换 run 的协议。 |
| `ModelRequest` | `systemPrompt/userPrompt/maxOutputTokens/attributes` | 发送给模型的提示、输出上限及模型适配器专用属性；不等同于 `AgentRequest.attributes`。 |
| `ModelResponse` | `text/toolCalls/finishReason/metrics/metadata` | 模型原文、供应商原生工具调用、供应商结束原因、标准指标、供应商专有响应元数据；`metadata` 不进入内核控制协议。 |
| `ModelToolCall` | `callId/toolName/arguments` | 模型原生工具调用的规范化载体；Codec 决定是否映射为内核动作。 |
| `ModelCallMetrics` | `provider/model/requestId/inputTokens/outputTokens/totalTokens/attributes` | 上报用模型身份、供应商请求 ID、非负 token 用量及供应商指标扩展值；`attributes` 仅供观测，不是决策 metadata。 |
| `AgentModelRetryOptions` | `maxAttempts/initialDelay/maxDelay` | 可重试模型失败的总调用次数及退避边界；仅 `MODEL_CALL_FAILED` 且 `retryable=true` 触发。 |

## 协议、动作与工具

| 对象 | 字段 | 固定含义 |
| --- | --- | --- |
| `AgentProtocolId` | `protocolName/protocolVersion` | 结构化协议身份；`key()` 仅为可读标签，不代替两个分量。 |
| `AgentProtocolContext` | `request/state` | Codec 解析时的只读请求和当前状态。 |
| `AgentDecision` | `actions/metadata` | Codec 输出的至少一个协议无关动作、协议解析附属数据。后者由 Kernel 保存为 `agent.protocolMetadata`，不等于模型响应 metadata。 |
| `AgentAction` | `actionType/metadata()` | 动作分发类型；默认 `metadata()` 是扩展附属值，Kernel 不以它驱动状态转换，也不自动持久化。 |
| `ExtensionAction` | `actionType/payload` | 自定义动作类型与其协议载荷，由对应 Handler 解释。 |
| `PlanTask` / `TaskPlanAction` | `taskId/title/dependsOn`；`summary/tasks` | 计划节点身份、标题、依赖节点 ID；计划摘要和节点集合。计划不等于宿主工作流任务。 |
| `ToolCallAction` | `callId/taskId/toolName/arguments` | 一次工具操作 ID、可空计划节点关联、工具名、参数；四者组成快照中的操作身份。 |
| `ToolBatchAction` | `summary/toolCalls` | 独立工具批次摘要及至少两个无依赖工具调用；带依赖批次须自定义动作。 |
| `AnswerChunkAction` / `FinalAnswerAction` | `text` / `answer` | 非终态回答片段 / 最终回答动作文本。 |
| `ConfirmationAction` | `confirmationId/message/options` | 确认身份、给用户的等待提示和协议提供的确认选项；`options` 不控制 Kernel。 |
| `AgentActionContext` | `request/state/executionContext/clock` | Handler 当前只读输入及时间源；状态修改只能经 `AgentActionResult` 返回。 |
| `AgentActionResult` | `observations/requestedRunStatus` | 本动作产生的观察；可空的运行状态请求，`null` 表示继续，仅允许请求 `COMPLETED` 或 `SUSPENDED`。 |
| `AgentActionResult` | `answer/suspensionMessage` | 完成态只填最终回答；暂停态只填非空等待提示；非终态两者均为空。 |
| `AgentActionResult` | `stateAttributes/removedStateAttributes` | 对运行状态属性的写入与删除；先删后写，受 `agent.*`/`react.*` 所有权限制。 |
| `AgentToolContext` | `executionContext/callId/taskId/toolName/deadline` | 工具步骤上下文及操作身份；这里的 deadline 是单工具截止，不能超过 invocation deadline。`idempotencyKey()` 返回结构化 `(agentRunId, callId)`。 |
| `AgentToolIdempotencyKey` | `agentRunId/callId` | 工具持久化幂等键的两个结构化分量；不可只保存 `callId`。`ToolCallAction.operationDefinition()` 是包含工具名、关联任务和参数的操作定义，不可充当幂等键。 |
| `AgentToolDescriptor` | `toolName/description/inputSchema` | 策略可见的工具名、说明和输入结构定义；`inputSchema` 不是一次调用的参数。 |
| `AgentToolDescriptor` | `readOnly/requiresConfirmation/metadata` | 是否只读、是否要求执行前确认、工具目录附属信息；`metadata` 不等于模型或协议 metadata。 |
| `ToolExecutionResult` | `success/progress/output/errorCode/errorMessage/attributes` | 工具权威结果；`progress` 表示动作推进循环状态，不代表外部副作用提交；失败必须有稳定错误码，属性进入标准工具 Observation。 |
| `AgentObservation` | `actionType/success/progress/output/errorCode/errorMessage/attributes` | 下轮策略可见且进入快照的动作反馈；标准工具的属性固定含 `callId/toolName` 及可选 `taskId`，工具自定义值不得覆盖这些身份。 |

## 状态、停止、恢复与观测

| 对象 | 字段 | 固定含义 |
| --- | --- | --- |
| `AgentRunState` / `AgentRunSnapshot` | `agentRunId/task/protocolId/runStatus/turn` | 当前 run 身份、完整任务、固定协议、运行状态、累计轮数；快照只由 Kernel 投影，`version = 3`。 |
| `AgentRunState` / `AgentRunSnapshot` | `startedAt/updatedAt/observations/attributes` | run 初始时间、最近状态更新时间、累计观察、持久化运行属性。`attributes` 含 Kernel `agent.*`、ReAct `react.*` 和扩展命名空间，不等同于请求属性。 |
| `AgentRunState` / `AgentRunSnapshot` | `consecutiveProtocolErrors/consecutiveNoProgress` | 连续协议纠错次数与连续无进展次数，成功决策或有效动作按规则清零。 |
| `AgentRunState` / `AgentRunSnapshot` | `answer/suspensionMessage/stopDecision` | 只在 `COMPLETED` 有最终回答；只在 `SUSPENDED` 可有等待提示；非运行态有停止决定。v1/v2 快照不能直接恢复为 v3，须由宿主显式迁移。 |
| `AgentResult` | `agentRunId/runStatus/answer/stopDecision/snapshot/failure` | 单次同步调用结果；只有完成态可有 `answer`。`snapshot` 是返回时状态投影，不代表宿主已成功持久化；技术故障通过可空 `failure` 表达。 |
| `AgentFailure` | `errorCode/errorMessage/retryable` | 活动循环技术失败的稳定枚举、说明及可重试标记；不得依赖消息文本作分支。 |
| `AgentStopDecision` | `shouldStop/stopReason/runStatus/stopMessage` | 是否停止、枚举原因、目标运行状态、可读说明；暂停动作的等待提示由 `stopMessage` 对外返回，说明文本不是分支依据。 |
| `AgentResumeInput` | `reconciledObservations` | 核验暂停后由宿主提供的完整权威工具结果；普通恢复为空。 |
| `AgentStopContext` | `request/state/executionContext/now` | 一次停止策略评估的只读输入及评估时刻。 |
| `AgentLifecycleContext` | `version/eventType/invocationType/eventSequence` | 固定观测协议版本、事件枚举、run 或 resume、invocation 内事件序号；当前版本为 3。 |
| `AgentLifecycleContext` | `agentRunId/invocationId/stepId/parentStepId/stepType/operation` | 事件追踪身份；`eventId()` 按 invocation 与序号派生，不是额外存储字段。 |
| `AgentLifecycleContext` | `turn/actionIndex/occurredAt/executionStartedAt/deadline/elapsed` | 当前轮次、动作位置、事件时间、invocation 起点与截止、该事件对应步骤耗时；`eventStatus()` 是事件状态，不是 `AgentRunStatus`。 |

`AgentRunStatus` 是 run 的 `RUNNING/SUSPENDED/COMPLETED/FAILED/CANCELLED`；`AgentLifecycleStatus` 是单条观测的 `STARTED/COMPLETED/FAILED/STOPPED`。协议错误用 `AgentProtocolException` 的协议扩展码，前置契约错误用 `AgentCoreException.errorCode`，运行技术故障用 `AgentFailure`，工具明确失败用 `AgentObservation.errorCode`；这四者不能互换。

`AgentCoreException.retryable` 只有模型适配器抛出 `MODEL_CALL_FAILED` 时参与内置模型重试；`AgentProtocolException.protocolErrorCode` 是所属 Codec 的可纠正细分类，不能代替 `AgentErrorCode`。`AgentStopReason`、`AgentLifecycleEventType` 和 `AgentLifecycleStepType` 均是固定枚举；宿主按枚举判断，消息字段仅供阅读。`AgentCancellation` 是一次活动请求的可变协作信号，不能在无关请求间共享。
