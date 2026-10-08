# kset-agent-core

框架无关的 Agent 循环内核。模块固定循环、停止与恢复语义，默认提供 ReAct 推理策略和 `agent-json:v1` 协议，同时允许宿主扩展其他推理方式、模型协议和动作。

## 引入

```xml
<dependency>
    <groupId>com.kset</groupId>
    <artifactId>kset-agent-core</artifactId>
</dependency>
```

模块只依赖 Java 21 与 Jackson，不包含 Spring 自动装配、数据库、Redis、Web、权限或 RAG 业务模型。

## 稳定内核

循环语义固定为：

```text
RunState -> ModelResponse -> Decision -> Action -> Observation -> StopDecision
```

| 包 | 职责 |
| --- | --- |
| `api` | 请求、结果和运行状态 |
| `loop` | `AgentLoopKernel`、不可变运行状态、快照与构建器 |
| `model` | 模型调用及原生 Function Calling 响应抽象 |
| `protocol` | 版本化 Codec 和注册表 |
| `strategy` | 推理策略扩展与默认 ReAct 策略 |
| `action` | 标准动作、动作处理器与注册表 |
| `tool` | 通用工具描述、注册与结构化执行结果 |
| `stop` | 集中式停止判断及扩展策略 |
| `checkpoint` | 宿主持久化端口 |
| `event` | 只读生命周期监听器 |

## 最小接入

```java
AgentModel model = request -> ModelResponse.text(callYourModel(request));

InMemoryAgentToolRegistry tools = new InMemoryAgentToolRegistry();
tools.register(yourTool);

AgentLoopKernel kernel = AgentLoopKernel.builder(model)
        .tools(tools)
        .toolExecutor(yourExecutor)
        .build();

AgentRequest request = AgentRequest.of(
        "分析任务并返回结果",
        AgentJsonV1Codec.ID);
AgentResult result = kernel.run(request);
```

构建器默认使用同步 `Executor`，不会创建或持有线程。需要工具并行执行时，宿主必须通过 `toolExecutor` 提供有界线程池并负责其生命周期。

## 停止语义

`AgentStopController` 是所有循环出口的单一入口，依次处理：

1. 用户取消和线程中断。
2. 总超时。
3. 完成或等待外部输入。
4. 最大轮次、连续协议错误和连续无进展。
5. 宿主注册的附加 `AgentStopPolicy`。

宿主策略只能追加停止条件，不能取消内核已经作出的停止决定。每个返回结果都携带 `AgentStopReason` 和最新 `AgentRunSnapshot`。

## 协议扩展

`AgentProtocolCodec` 负责两个方向：

- 在模型调用前补充当前协议说明。
- 将模型响应转换为协议无关的 `AgentDecision`。

新增协议只需实现 Codec 并注册：

```java
AgentLoopKernel kernel = AgentLoopKernel.builder(model)
        .protocol(new NativeFunctionCallingCodec())
        .build();
```

内置 `agent-json:v1` 使用 `<<<AGENT_JSON>>>` / `<<<END_AGENT_JSON>>>` 严格包裹控制 JSON；无标记普通文本按最终回答处理。协议支持 `task_plan`、`tool_call`、`tool_batch`、`answer_chunk`、`final_answer` 和 `confirmation`。

自定义协议可以映射到标准动作，也可以返回 `ExtensionAction`。自定义动作必须同时注册对应 `AgentActionHandler`；未注册动作会显式失败。

## 推理策略扩展

`AgentReasoningStrategy` 只负责生成本轮模型请求和校验语义动作。默认 `ReactReasoningStrategy` 的 planning、acting、evaluating、answering 阶段不会进入循环内核。

接入 Plan-and-Execute、Supervisor 或其他策略时，实现 `AgentReasoningStrategy` 并通过构建器替换即可，无需修改协议注册和停止控制。

## 中断与恢复

`AgentRunSnapshot` 不保存模型原始思维链，只保存轮次、标准观察、扩展属性、错误计数和停止结果。宿主可实现 `AgentCheckpointPort`，然后调用：

```java
kernel.resume(request);             // 从 AgentCheckpointPort 查询
kernel.resume(request, snapshot);   // 使用已取得的快照
```

同一运行恢复时不能切换协议，防止旧快照被不同语义解释。

内核会在启动、协议错误、每个动作结果和停止时保存快照。外部工具副作用无法与快照存储形成通用原子事务，工具适配器应以 `ToolCallAction.callId` 实现幂等；检查点写入失败会使本次运行以 `FAILED` 返回，不能继续推进后续动作。

## 迁移说明

本轮直接替换了未发布的旧 API。原 `AgentReActExecutor`、`StateGraphWorkflowEngine`、Spring 自动装配以及项目/文档/代码仓库/权限/指标端口已移除。需要旧实现时可从 Git ref `a7a63f2` 恢复。
