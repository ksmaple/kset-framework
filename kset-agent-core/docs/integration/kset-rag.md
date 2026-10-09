# kset-rag 接入映射

本文记录 `kset-rag` 当前 `agent-json-v1`、模型供应商协议和 SSE 输出与 `kset-agent-core` 的边界。它是接入约定，不把 RAG 业务类型引入 core。

## 三层协议

| 层次 | kset-rag 当前实现 | core 接入方式 |
| --- | --- | --- |
| 模型传输 | `openai-compatible`、`anthropic-native`、`azure-openai`、`ollama` | 宿主 `AgentModel` 适配器将 Spring AI 响应转换为 `ModelResponse` |
| Agent 控制 | `agent-json-v1` / `code-v7` | 独立 `AgentProtocolCodec` 转换为 `AgentDecision` |
| 用户输出 | SSE `task/status/step/done/error` | Listener、Checkpoint 和最终 `AgentResult` 的宿主投影 |

SSE 不是模型协议，不进入 Codec。会话单飞、任务租约、确认接口和断流恢复也继续由 kset-rag 工作流负责。

## 协议身份

kset-rag `code-v7` 与内置 `agent-json:v1` 使用相同标记和六个动作名，但 wire 字段与执行语义不同，不能复用内置 ID。建议注册：

```java
new AgentProtocolId("kset-rag-json", "code-v7")
```

禁止覆盖 `AgentJsonV1Codec.ID`。

## 字段映射

| kset-rag 输出 | core 动作 | 适配要求 |
| --- | --- | --- |
| 无标记普通文本 | `FinalAnswerAction` | 只允许作为无副作用最终回答 |
| `task_plan` | `TaskPlanAction` | `progressSummary` 映射 summary；`queryAnalysis` 放 metadata，并由 Strategy 持久化 |
| `tool_call` | `ToolCallAction` | `planTaskId` 映射 taskId；缺省 callId 由宿主生成并立即持久化 |
| 无依赖 `tool_batch` | `ToolBatchAction` | `progressSummary` 映射 summary |
| 带 `dependsOn` 的 `tool_batch` | `ExtensionAction("kset-rag.tool-batch", payload)` | kset-rag Handler 按依赖 DAG 分层执行，不能使用标准并发 Handler |
| `answer_chunk` | `AnswerChunkAction` | `progressSummary`、记忆字段放 metadata |
| `final_answer` | `FinalAnswerAction` | `resourceRefs`、记忆字段放 metadata，由 Listener/结果装配器读取 |
| `confirmation` | `ConfirmationAction` | 宿主生成稳定 confirmationId；完整卡片字段放 options/metadata；恢复请求传入同一 ID 后标准 Handler 会消费确认并继续 |
| `ToolObservation` | `AgentObservation` | 结果放 output，证据等级和资源放 attributes，错误使用稳定 errorCode |

## 必需宿主组件

| 组件 | 职责 |
| --- | --- |
| `KsetRagAgentModel` | 包装现有 Spring AI 调用，传递 deadline/cancellation，过滤 thinking 内容并填充 `ModelCallMetrics` |
| `KsetRagAgentJsonCodec` | 严格解析 `code-v7` 并完成上表映射 |
| `KsetRagReasoningStrategy` | 保留现有阶段、允许动作、queryAnalysis 和证据完成规则 |
| `KsetRagToolAdapter` | 权限校验后把现有 ToolRegistry 暴露为 core 工具 |
| `KsetRagDependentBatchHandler` | 执行带 dependsOn 的批次扩展动作 |
| `KsetRagCheckpointPort` | 将不可变快照条件写入现有工作流状态存储 |
| `KsetRagLifecycleListener` | 把固定生命周期事件投影为步骤、SSE、日志和工作流指标 |

## callId

`callId` 是工具副作用幂等键，新协议应优先要求模型显式返回。兼容旧输出缺省时，宿主可以按 `runId + turn + 当前决策内序号` 生成首次调用 ID，并在动作执行前随快照或待确认动作持久化。同一操作在确认、结果未知重试和恢复时必须读取并复用已保存 ID，不得重新生成。

现有 `planTaskId + "#" + 序号` 只能保证单个决策内唯一，同一计划任务跨轮再次调用时可能碰撞，不能直接作为稳定幂等键。

## metadata 保留

建议使用 `kset.rag.*` 属性命名空间。`queryAnalysis` 必须由 Strategy 从 Decision metadata 复制到持久属性，不能依赖只保留最近一次值的 `agent.protocolMetadata`。`resourceRefs`、`memoryPriority` 和 `memorySummary` 可在 `afterDecision` 事件中立即投影到宿主结果。

## 观测映射

| core 端点 | kset-rag 现有能力 |
| --- | --- |
| `beforeRun/beforeTurn` | 工作流开始日志、轮次步骤和执行阶段 |
| `beforeModel/afterModel` | 模型耗时、Token 用量、provider/model、finishReason |
| `afterDecision` | 协议决策步骤、progressSummary、resourceRefs 和记忆建议 |
| `beforeAction/afterAction` | 工具运行步骤、callId、成功率、错误码和工具耗时 |
| `afterTurn` | 单轮决策与动作汇总、整轮耗时和最新运行状态 |
| `onProtocolError` | 协议重试步骤和错误指标 |
| `beforeCheckpoint/afterCheckpoint` | 带 owner/fencing 的条件写及持久化耗时 |
| `onStop/onError/afterRun` | 最终任务状态、SSE done/error 和工作流汇总指标 |
| `onListenerError` | 上报器失败、队列丢弃与告警指标 |

`KsetRagLifecycleListener` 使用 `invocationId + sequence` 作为事件幂等身份，使用 `runId + turn + actionIndex/callId` 关联步骤。模型原文、Prompt、工具参数、Observation 和请求属性不得直接写日志；现有全量模型错误日志在接入前必须改为长度、指纹及脱敏摘要。

## 并发与恢复

- 一个会话只能有一个活动实例；接入前必须补充基于 `sessionId` 的原子抢占或活动任务唯一约束，现有按 `taskId` 的租约不能替代会话单飞。
- 不同会话可以并发调用同一个 Kernel；所有共享扩展必须线程安全。
- core 的 `resume` 只恢复逻辑快照，不负责读取快照、重新抢占任务或判断未知工具结果。
- 带副作用的工具恢复前必须按原 callId 查询权威结果，再决定复用结果、重试或补偿。
- kset-rag 的 Strategy 只写 `kset-rag.*`，扩展 Action Handler 也使用该宿主命名空间，不得改写 core 的 `agent.*`/`react.*`。

## 接入顺序

1. 引入 `kset-agent-core`，实现 Model 和 Tool 适配器。
2. 注册 `kset-rag-json:code-v7` Codec 和 RAG Strategy。
3. 为带依赖工具批次注册扩展 Action Handler。
4. 接入 CheckpointPort 和 Listener，保持现有 SSE DTO 不变。
5. 将单次工作流执行替换为 `kernel.run/resume`，会话锁和恢复入口保持在宿主层。
