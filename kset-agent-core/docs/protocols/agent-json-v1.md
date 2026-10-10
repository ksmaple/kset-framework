# agent-json:v1 协议

`agent-json:v1` 是 `kset-agent-core` 内置且不可覆盖的模型输出协议。它只定义模型响应到内核动作的 wire 映射，不定义模型供应商 HTTP 协议、SSE 协议或业务工作流。

## 边界

- 协议 ID：`agent-json:v1`
- 开始标记：`<<<AGENT_JSON>>>`
- 结束标记：`<<<END_AGENT_JSON>>>`
- 每轮最多一个标记信封，信封外不得有内容。
- 信封内必须是单个 JSON 对象，重复字段、未知字段、尾随对象和 `null` 占位均不属于有效输入。
- 无标记且不是控制 JSON 的普通文本映射为 `final_answer`。
- 无标记但包含受支持 `type` 的控制 JSON 返回 `MISSING_MARKERS`。

## 动作

### task_plan

```json
{
  "type": "task_plan",
  "plan": "Locate the relevant implementation",
  "tasks": [
    {"taskId": "locate", "title": "Locate entry points", "dependsOn": []}
  ],
  "metadata": {}
}
```

`plan`、非空 `tasks`、每个任务的 `taskId` 和 `title` 必填。任务 ID 必须唯一，依赖必须引用当前计划中的其他任务，且不得形成环路。

### tool_call

```json
{
  "type": "tool_call",
  "toolCall": {
    "callId": "locate#1",
    "taskId": "locate",
    "toolName": "searchCode",
    "arguments": {"query": "AgentLoopKernel"}
  },
  "metadata": {}
}
```

`callId`、`toolName` 和对象类型 `arguments` 必填。`callId` 在整个 run 内只能标识一个工具操作；后续轮次的不同操作必须使用新 ID，确认、重试、核验和恢复同一副作用时必须复用原 ID。Kernel 会在单个决策内判重，并把 `callId/taskId/toolName/arguments` 身份写入快照账本；跨轮同 ID 异身份以可纠正的 `TOOL_IDEMPOTENCY_CONFLICT` 拒绝。持久化时仍须使用结构化 `(runId, callId)` 作为操作身份，并保存 `toolName + 规范化 arguments` 指纹，作为跨进程和外部副作用的最终防线；同身份异指纹必须返回 `TOOL_IDEMPOTENCY_CONFLICT`。标准工具 Observation 的 attributes 固定返回 `callId`、可选 `taskId` 和 `toolName`。

### tool_batch

```json
{
  "type": "tool_batch",
  "plan": "Inspect independent sources",
  "toolCalls": [
    {"callId": "locate#1", "taskId": "locate", "toolName": "searchCode", "arguments": {}},
    {"callId": "locate#2", "taskId": "locate", "toolName": "searchDocs", "arguments": {}}
  ],
  "metadata": {}
}
```

至少包含两个调用。标准批处理语义是所有调用互相独立并可并发执行，不支持调用间 `dependsOn`。有依赖的批次必须使用自定义动作和 Handler。

已有批次任务提交后发生超时、线程中断或汇总异常时，标准处理器返回 `TOOL_RESULT_UNKNOWN`，Kernel 以 `RECONCILIATION_REQUIRED + SUSPENDED` 停止，不会继续下一轮。该核验停止优先于批次等待期间同时出现的通用超时或线程中断；宿主必须逐个查询 `(runId, callId)` 的权威结果后再恢复，禁止直接重新生成 callId 或盲目重试。

工具适配器自身发现远程结果未知时必须返回 `ToolExecutionResult.failure(TOOL_RESULT_UNKNOWN, ...)`。单工具调用会保存原动作并暂停；批次中任一调用未知会保留全部 Observation、保存完整批次并暂停。明确失败仍返回其具体工具错误码，由 Strategy 决定是否继续。

核验完成后，宿主使用 `AgentObservation.toolResult` 为 pending 中每个调用构造一条携带相同 `callId/taskId/toolName` 的权威 `tool_call` Observation，通过 `AgentResumeInput` 调用三参数 `resume`。结果必须完整覆盖 pending 调用且不得继续使用 `TOOL_RESULT_UNKNOWN`；空输入、缺项或身份不匹配会在进入模型循环前分别以 `INVALID_RESUME_INPUT` 或 `PENDING_ACTION_MISMATCH` 拒绝。

### answer_chunk

```json
{"type":"answer_chunk","answer":"Partial answer","metadata":{}}
```

片段按执行顺序由标准 Handler 累积；片段开始后，默认 ReAct 策略只允许继续片段或返回最终回答。

### final_answer

```json
{"type":"final_answer","answer":"Final answer","metadata":{}}
```

该动作结束本次运行。此前的回答片段会与最终正文合并。

### confirmation

```json
{
  "type": "confirmation",
  "confirmation": {
    "confirmationId": "confirm-locate#1",
    "message": "Allow this operation?",
    "options": {"actionCallId": "locate#1"}
  },
  "metadata": {}
}
```

`confirmationId` 和 `message` 必填。未确认时该动作将运行置为 `SUSPENDED`；宿主保存快照并在恢复请求中通过 `AgentRequest.CONFIRMED_ACTION_IDS` 提供已确认的稳定动作 ID。恢复后动作必须与 `agent.pendingAction` 完整匹配，标准 Handler 才会消费确认并继续循环；在新 run 中预置确认 ID 或确认后修改动作内容都会返回 `PENDING_ACTION_MISMATCH`。危险工具采用相同规则，并额外绑定 `callId/taskId/toolName/arguments` 的完整操作身份。

## metadata

根层 `metadata` 是协议中立的扩展数据，只能是 JSON 对象。内核将最近一次 metadata 保存到快照属性 `agent.protocolMetadata`，但不会解释业务内容。需要跨多轮保留的数据应由自定义 Strategy 显式复制到自己的非保留命名空间。

`agent.*` 和 `react.*` 是内核保留属性前缀。请求侧唯一公开的内核属性是 `agent.confirmedActionIds`；宿主不得写入快照中的内部工具操作或确认账本，扩展应使用自己的命名空间。`agent.pendingAction` 只在等待确认或标准工具结果核验时保留，并在确认消费、工具结束、核验恢复或最终回答时由内核删除。内部账本不会进入默认 ReAct Prompt。

`runId`、`invocationId`、`stepId`、`parentStepId`、`stepType` 和 `operation` 属于 Kernel 生成的固定执行追踪信封，不属于模型 JSON 字段。Codec 不得从模型输出读取或覆盖这些身份；外部适配器应从 `AgentExecutionContext`、`AgentToolContext` 或 `AgentLifecycleContext` 获取。

## 错误码

内置 Codec 通过 `AgentProtocolException.protocolErrorCode()` 返回 `AgentJsonV1ErrorCode`：

`EMPTY_RESPONSE`、`MISSING_MARKERS`、`INVALID_MARKERS`、`MULTIPLE_ENVELOPES`、`OUTSIDE_CONTENT`、`INVALID_JSON_BOUNDARY`、`INVALID_JSON`、`UNSUPPORTED_ACTION`、`INVALID_SCHEMA`、`INVALID_PLAN`、`INVALID_TOOL_BATCH`、`INVALID_TOOL_CALL`、`INVALID_TOOL_ARGUMENTS`、`INVALID_CONFIRMATION`、`INVALID_METADATA`、`UNKNOWN_FIELD`、`MISSING_FIELD`。

这些错误属于可纠正模型协议错误，内核按 `protocolErrorLimit` 继续纠正或停止。调用方不得依赖错误消息文本分支。

## 版本规则

- 修正文案但不改变字段语义时保留 `v1`。
- 新增可选字段、改变字段含义或动作执行语义时必须发布新版本。
- 自定义协议必须使用独立名称或版本，不能注册为 `agent-json:v1`。
- 同一 `runId` 的 `resume` 必须继续使用快照中的协议 ID。
