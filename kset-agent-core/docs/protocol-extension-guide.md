# 协议扩展指南

循环内核只消费 `AgentDecision`。模型供应商格式、文本控制格式或原生 Function Calling 都应通过 `AgentProtocolCodec` 转换，不得修改 `AgentLoopKernel`。

## 扩展步骤

1. 分配稳定 `AgentProtocolId`，名称和版本都不可为空。
2. 在 `prepare` 中追加当前协议的模型输出约束。
3. 在 `decode` 中把完整 `ModelResponse` 转换为标准动作或 `ExtensionAction`。
4. 通过 Builder 注册 Codec；重复 ID 会以 `INVALID_CONFIGURATION` 构建失败。
5. 在 `AgentRequest.protocolId` 中选择本次运行的协议。
6. 自定义动作同时注册唯一的 `AgentActionHandler`。

注册表以结构化 `AgentProtocolId(protocolName, protocolVersion)` 作为唯一键，Codec 的 `protocolId()` 只在构建期读取一次；`protocolName + ":" + protocolVersion` 仅用于展示，不能作为注册或持久化主键。

## 原生 Function Calling 示例

```java
public final class NativeFunctionCallingCodec implements AgentProtocolCodec {
    public static final AgentProtocolId ID =
            new AgentProtocolId("native-function-calling", "v1");

    @Override
    public AgentProtocolId protocolId() {
        return ID;
    }

    @Override
    public AgentDecision decode(ModelResponse response, AgentProtocolContext context) {
        if (response.toolCalls().isEmpty()) {
            if (response.text().isBlank()) {
                throw new AgentProtocolException("EMPTY_RESPONSE", "model response is empty");
            }
            return AgentDecision.of(new FinalAnswerAction(response.text()));
        }

        List<ToolCallAction> toolCalls = response.toolCalls().stream()
                .map(call -> new ToolCallAction(
                        requiredCallId(call), null, call.toolName(), call.arguments()))
                .toList();
        if (toolCalls.size() == 1) {
            return AgentDecision.of(toolCalls.get(0));
        }
        return AgentDecision.of(new ToolBatchAction("Execute native tool calls", toolCalls));
    }

    private String requiredCallId(ModelToolCall call) {
        if (call.callId() == null || call.callId().isBlank()) {
            throw new AgentProtocolException(
                    "MISSING_TOOL_CALL_ID", "native tool call id is required");
        }
        return call.callId();
    }
}
```

上例只展示协议转换。若 Strategy 要求工具必须引用计划任务，Codec 或自定义 Strategy 还必须提供有效 `taskId`。

## 注册与选择

```java
NativeFunctionCallingCodec codec = new NativeFunctionCallingCodec();
AgentLoopKernel kernel = AgentLoopKernel.builder(model)
        .protocol(codec)
        .runIdGenerator(new SnowflakeAgentIdGenerator(datacenterId, workerId))
        .build();

AgentRequest request = AgentRequest.of(
        "Inspect the repository",
        NativeFunctionCallingCodec.ID);
AgentResult result = kernel.run(request);
```

每次新运行可以选择任意已注册协议。运行开始后，Strategy 不能改变协议；恢复时请求协议必须与快照协议一致。

## 标准动作与扩展动作

优先映射到标准动作：

| 语义 | 动作 |
| --- | --- |
| 创建无环任务计划 | `TaskPlanAction` |
| 单个工具调用 | `ToolCallAction` |
| 互相独立的工具批次 | `ToolBatchAction` |
| 回答分片 | `AnswerChunkAction` |
| 最终回答 | `FinalAnswerAction` |
| 暂停等待确认 | `ConfirmationAction` |

当外部协议包含标准动作没有表达的执行语义，例如工具调用依赖 DAG、业务审批状态或多角色委派，应返回具有独立类型名的 `ExtensionAction`，并注册对应 Handler。不得把依赖字段塞入 metadata 后继续交给会忽略它的标准 Handler。

## metadata

`AgentDecision.metadata` 适合承载引用、记忆建议、供应商使用量等不改变动作执行语义的数据。内核只保留最近一次协议 metadata。跨轮状态由 Strategy 写入自己的属性命名空间；业务输出可由 Listener 在 `afterDecision` 中投影。

模型供应商、模型名、请求 ID 和 Token 数量不放入协议 metadata，应由 Model Adapter 写入 `ModelResponse.metrics`。供应商特有的可观测字段放入 `ModelCallMetrics.attributes`，业务决策数据仍放在 `AgentDecision.metadata`，两者不得混用。

## 错误处理

- 可由模型纠正的格式错误：抛出 `AgentProtocolException`，提供稳定 `protocolErrorCode`。
- Codec 实现故障：让运行异常传播，内核转换为 `PROTOCOL_CODEC_FAILED`。
- 未注册协议：`AgentCoreException(PROTOCOL_NOT_REGISTERED)`。
- 未注册扩展动作：`AgentCoreException(ACTION_NOT_REGISTERED)`。
- Codec、Handler 或 Strategy 返回 `null`：`EXTENSION_CONTRACT_VIOLATION`。

附加 `AgentStopPolicy` 只能追加停止条件，不得返回 `COMPLETED` 代替产生最终回答的 Action；这种返回会转换为 `EXTENSION_CONTRACT_VIOLATION`。只有标准 `ToolCallAction` 和 `ToolBatchAction` 的 `TOOL_RESULT_UNKNOWN` 会进入内置 `RECONCILIATION_REQUIRED`。扩展动作即使返回同名错误码，也仍按其 `SUSPENDED` 结果解释为 `WAITING_INPUT`，必须由扩展自己的恢复协议处理。

自定义 Codec、Strategy 和 Handler 会被 Kernel 单例并发调用，必须线程安全，不得保存单次运行的可变状态。

## 状态扩展边界

- 自定义 Handler 通过 `AgentActionResult.stateAttributes` 写状态，通过 `removedStateAttributes` 删除状态；同一结果不能同时写入和删除同一键。
- 自定义 Handler 不得修改 `agent.*` 或 `react.*`，应使用宿主自己的命名空间。
- Strategy ID 在 Kernel 构建期固定；`afterTurn` 只能修改 `{strategyId}.*`，其他字段或属性变化会返回 `EXTENSION_CONTRACT_VIOLATION`。
- `agent.pendingAction` 由标准确认、工具和最终回答 Handler 管理，扩展动作需要等待外部输入或核验未知结果时应使用自己的 pending 命名空间与恢复输入。

## 生命周期观测

`AgentLifecycleListener` 为每个 `run/resume` 生成独立 invocation，并通过固定 `AgentLifecycleEventType` 暴露调用、轮次、模型、决策、动作、协议错误、检查点和最终结果端点。`AgentLifecycleContext.eventSequence` 在 invocation 内严格递增，`actionIndex` 为零基序号，非动作事件固定为 `NO_ACTION`。模型、动作和检查点完成事件的 `elapsed` 只计算实际扩展调用，轮次完成事件计算整轮耗时，最终返回事件计算本次 invocation 总耗时。

调用追踪信封属于固定内核协议，不属于 Codec 的外部数据格式。Kernel 生成 `runId/invocationId/stepId/parentStepId/stepType/operation` 并写入 `AgentExecutionContext`；Model、Action、Tool 和 StopPolicy 只能读取，外部协议不得声明、覆盖或重映射这些字段。Model 获得 model step，Action 及其 Tool 获得 action step，Tool 使用 `callId` 区分同一动作内的具体操作。

完成事件只表示同步扩展调用已经返回，不表示检查点已经持久化；可靠恢复以 `afterCheckpoint` 对应的 `AgentCheckpointPort.save` 成功为准。保存失败会发出 `CHECKPOINT_FAILED` 并调用 best-effort `onCheckpointError`，该回调异常不能覆盖原检查点错误。独立健康 Listener 可通过 `onListenerError` 接收其他 Listener 的失败信息。Listener 必须线程安全，不得保存无界的按 run 可变状态。日志和指标实现不得输出未经脱敏的模型文本、工具参数或 Observation 内容。

同一个 Kernel 可以并发处理不同 run，因为运行状态和生命周期序号均为 invocation 局部数据。所有构建期注入对象会被不同调用共享，必须线程安全。扩展放入 Request、Decision、Action、Observation、Snapshot 或指标 attributes 的 Map、List、Set 和数组会被递归快照为只读结构，数组统一规范化为 List；不透明业务 DTO 不做反射复制，仍必须不可变或按调用隔离。
