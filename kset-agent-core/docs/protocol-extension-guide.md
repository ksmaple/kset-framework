# 协议扩展指南

循环内核只消费 `AgentDecision`。模型供应商格式、文本控制格式或原生 Function Calling 都应通过 `AgentProtocolCodec` 转换，不得修改 `AgentLoopKernel`。

## 扩展步骤

1. 分配稳定 `AgentProtocolId`，名称和版本都不可为空。
2. 在 `prepare` 中追加当前协议的模型输出约束。
3. 在 `decode` 中把完整 `ModelResponse` 转换为标准动作或 `ExtensionAction`。
4. 通过 Builder 注册 Codec；重复 ID 会以 `INVALID_CONFIGURATION` 构建失败。
5. 在 `AgentRequest.protocol` 中选择本次运行的协议。
6. 自定义动作同时注册唯一的 `AgentActionHandler`。

## 原生 Function Calling 示例

```java
public final class NativeFunctionCallingCodec implements AgentProtocolCodec {
    public static final AgentProtocolId ID =
            new AgentProtocolId("native-function-calling", "v1");

    @Override
    public AgentProtocolId id() {
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

        List<ToolCallAction> calls = response.toolCalls().stream()
                .map(call -> new ToolCallAction(
                        requiredCallId(call), null, call.name(), call.arguments()))
                .toList();
        if (calls.size() == 1) {
            return AgentDecision.of(calls.get(0));
        }
        return AgentDecision.of(new ToolBatchAction("Execute native tool calls", calls));
    }

    private String requiredCallId(ModelToolCall call) {
        if (call.id() == null || call.id().isBlank()) {
            throw new AgentProtocolException(
                    "MISSING_TOOL_CALL_ID", "native tool call id is required");
        }
        return call.id();
    }
}
```

上例只展示协议转换。若 Strategy 要求工具必须引用计划任务，Codec 或自定义 Strategy 还必须提供有效 `taskId`。

## 注册与选择

```java
NativeFunctionCallingCodec codec = new NativeFunctionCallingCodec();
AgentLoopKernel kernel = AgentLoopKernel.builder(model)
        .protocol(codec)
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

## 错误处理

- 可由模型纠正的格式错误：抛出 `AgentProtocolException`，提供稳定 `protocolCode`。
- Codec 实现故障：让运行异常传播，内核转换为 `PROTOCOL_CODEC_FAILED`。
- 未注册协议：`AgentCoreException(PROTOCOL_NOT_REGISTERED)`。
- 未注册扩展动作：`AgentCoreException(ACTION_NOT_REGISTERED)`。
- Codec、Handler 或 Strategy 返回 `null`：`EXTENSION_CONTRACT_VIOLATION`。

自定义 Codec、Strategy 和 Handler 会被 Kernel 单例并发调用，必须线程安全，不得保存单次运行的可变状态。
