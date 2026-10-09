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

`callId`、`toolName` 和对象类型 `arguments` 必填。确认、重试和恢复同一副作用时必须复用原 `callId`。

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

`confirmationId` 和 `message` 必填。未确认时该动作将运行置为 `SUSPENDED`；宿主保存快照并在恢复请求中通过 `AgentRequest.CONFIRMED_ACTION_IDS` 提供已确认的稳定动作 ID。恢复后若模型再次返回相同 `confirmationId`，标准 Handler 将其消费为成功 Observation 并继续循环，不会再次暂停。

## metadata

根层 `metadata` 是协议中立的扩展数据，只能是 JSON 对象。内核将最近一次 metadata 保存到快照属性 `agent.protocolMetadata`，但不会解释业务内容。需要跨多轮保留的数据应由自定义 Strategy 显式复制到自己的非保留命名空间。

`agent.*` 和 `react.*` 是内核保留属性前缀。请求侧唯一公开的内核属性是 `agent.confirmedActionIds`；宿主扩展应使用自己的命名空间。`agent.pendingAction` 只在等待确认时保留，并在确认消费、工具结束或最终回答时由标准 Handler 删除。

## 错误码

内置 Codec 通过 `AgentProtocolException.protocolCode()` 返回 `AgentJsonV1ErrorCode`：

`EMPTY_RESPONSE`、`MISSING_MARKERS`、`INVALID_MARKERS`、`MULTIPLE_ENVELOPES`、`OUTSIDE_CONTENT`、`INVALID_JSON_BOUNDARY`、`INVALID_JSON`、`UNSUPPORTED_ACTION`、`INVALID_SCHEMA`、`INVALID_PLAN`、`INVALID_TOOL_BATCH`、`INVALID_TOOL_CALL`、`INVALID_TOOL_ARGUMENTS`、`INVALID_CONFIRMATION`、`INVALID_METADATA`、`UNKNOWN_FIELD`、`MISSING_FIELD`。

这些错误属于可纠正模型协议错误，内核按 `protocolErrorLimit` 继续纠正或停止。调用方不得依赖错误消息文本分支。

## 版本规则

- 修正文案但不改变字段语义时保留 `v1`。
- 新增可选字段、改变字段含义或动作执行语义时必须发布新版本。
- 自定义协议必须使用独立名称或版本，不能注册为 `agent-json:v1`。
- 同一 `runId` 的 `resume` 必须继续使用快照中的协议 ID。
