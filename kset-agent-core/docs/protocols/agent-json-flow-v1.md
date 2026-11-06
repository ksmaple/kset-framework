# agent-json-flow:v1

这是按需启用的只读工具依赖协议。Core 构建器调用 `readOnlyToolFlow()`，或 Runtime 构建器调用同名方法，再将 `AgentRequest.protocolId` 设为 `AgentJsonFlowV1Codec.ID`。原有 `agent-json:v1`、标准 `tool_call` 和 `tool_batch` 不变；本协议仍接受它们的格式。

## tool_flow

```json
{
  "type": "tool_flow",
  "plan": "Find two records and inspect their details",
  "toolCalls": [
    {"callId":"find-a","taskId":"inspect","toolName":"findA","arguments":{}},
    {"callId":"find-b","taskId":"inspect","toolName":"findB","arguments":{}},
    {"callId":"details","taskId":"inspect","toolName":"readDetails",
     "dependsOn":["find-a","find-b"],
     "arguments":{"ids":[
       {"$toolResult":{"callId":"find-a","path":"/id"}},
       {"$toolResult":{"callId":"find-b","path":"/id"}}
     ]}}
  ]
}
```

`toolCalls` 至少两个且不超过 `AgentLoopOptions.maxToolCallsPerBatch`；超限时以 `CAPACITY_LIMIT/FAILED` 停止，不执行工具。每个 `callId` 在同一流程中唯一，且不得复用本次 run 已观察或登记的工具调用 ID；标准工具调用也不得复用流程已观察的 ID。`dependsOn` 可省略，引用已完成调用的 ID；依赖不存在、重复、自引用或成环均为协议错误。`taskId` 在已有计划时必须属于该计划；同一流程内若任务 B 的计划依赖任务 A，B 对 A 的调用也必须声明直接或传递的调用依赖。

`$toolResult` 必须独占其所在对象，包含直接依赖调用的 `callId` 和 `path`。`path` 是 JSON Pointer，空字符串引用完整输出。引用可放在参数对象或数组的任意层级；引用目标不存在或为 `null` 时，该调用不执行并返回 `TOOL_RESULT_REFERENCE_MISSING`。普通参数中的显式 `null` 不受影响。

执行器按依赖层提交工具调用；同层调用可并行，下一层只在上一层结果确定后启动。前置调用失败时，下游返回 `TOOL_DEPENDENCY_FAILED`，其他独立分支仍可继续。Observation 按 `toolCalls` 原始顺序返回，并携带标准 `callId/taskId/toolName` 字段。批次与单调用沿用已有数量、deadline 和超时选项；超时或取消后不启动后续层。`CompletableFuture` 的取消不保证终止已启动的只读调用。

所有工具在执行前统一检查：必须标记 `readOnly=true` 且 `requiresConfirmation=false`，否则整批返回 `TOOL_FLOW_READ_ONLY_REQUIRED`，不执行任何调用。真实业务工具仍须按当前身份校验权限。该扩展没有标准工具动作的执行前 pending 快照和未知副作用核验，因此不得用于写入、创建、发送或其他有副作用的操作；异常退出后重新读取可能得到较新的数据。

依赖调度只保证调用顺序和参数取值，不保证外部数据源的读后可见性。读取副本、缓存或最终一致性接口时，工具适配器须使用权威读取、版本标识或明确的未就绪结果；不得把尚未可见的数据解释为确定不存在。
