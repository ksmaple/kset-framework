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

## 文档导航

- [内置 `agent-json:v1` 协议](docs/protocols/agent-json-v1.md)
- [自定义协议扩展指南](docs/protocol-extension-guide.md)
- [kset-rag 接入映射](docs/integration/kset-rag.md)

## 稳定内核

循环语义固定为：

```text
RunState -> ModelResponse -> Decision -> Action -> Observation -> StopDecision
```

| 包 | 职责 |
| --- | --- |
| `api` | 请求、结果和运行状态 |
| `execution` | 单次活动执行的 deadline、取消和调用上下文 |
| `loop` | `AgentLoopKernel`、不可变运行状态、快照与构建器 |
| `model` | 模型调用及原生 Function Calling 响应抽象 |
| `protocol` | 版本化 Codec 和注册表 |
| `strategy` | 推理策略扩展与默认 ReAct 策略 |
| `action` | 标准动作、动作处理器与注册表 |
| `tool` | 通用工具描述、注册与结构化执行结果 |
| `stop` | 集中式停止判断及扩展策略 |
| `checkpoint` | 宿主快照写入端口 |
| `event` | 只读生命周期监听器 |

## 最小接入

```java
AgentModel model = (request, context) ->
        ModelResponse.text(callYourModel(request, context.deadline()));

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

构建器默认使用同步 `Executor`，不会创建或持有线程。需要工具并行执行时，宿主必须通过 `toolExecutor` 提供有界线程池并负责其生命周期。构建器仅用于启动期组装；`build()` 会冻结工具集合，之后修改原工具注册表不会影响已创建的 Kernel。

`AgentLoopKernel` 可以作为单例供多个独立任务并发调用，但注入的 Model、Strategy、Codec、Handler、Tool、StopPolicy、CheckpointPort 和 Listener 必须线程安全。请求与扩展属性中的复杂对象必须可序列化，并且在一次运行期间不可变。

状态属性有明确所有权：`agent.*` 属于 Kernel，`react.*` 属于内置 ReAct；自定义 Action Handler 不得写入或删除这两个保留命名空间。自定义 Strategy 的 `id()` 必须非空且不能占用 `agent` 命名空间，`afterTurn` 只能修改 `{strategyId}.*`。违反边界时运行返回 `EXTENSION_CONTRACT_VIOLATION`。`AgentActionResult.removedStateAttributes` 显式表达删除，Kernel 总是先删除再合并新值。

## 对外结果与错误契约

核心把“为何停止”和“哪里出错”分开表达：

| 类型 | 用途 | 调用方处理方式 |
| --- | --- | --- |
| `AgentStopReason` | 每次循环退出的稳定原因，包括完成、暂停、取消、超时和容量限制 | 始终读取 `AgentResult.stop()` |
| `AgentErrorCode` | 请求、配置、快照、协议、扩展及技术故障的稳定分类 | 禁止依赖异常消息做分支 |
| `AgentCoreException` | `run/resume` 前置校验和 Kernel 组装失败 | 读取 `code()` 与 `retryable()` |
| `AgentProtocolException` | Codec 或 Strategy 报告的可纠正协议错误，`protocolCode()` 提供协议扩展码 | Kernel 计入协议错误次数并继续，达到上限后停止 |
| `AgentFailure` | 活动循环中的 Model、Strategy、Codec、Action、Checkpoint 或关键 Listener 技术失败 | 先读取 `stop()`，`failure()` 非空时按技术故障处理 |
| `AgentObservation.errorCode` | 单个工具调用的结构化错误，如未找到、取消、超时或执行失败 | 工具错误作为 Observation 返回，Strategy 可决定是否继续 |

正常完成、等待输入、取消、活动超时、最大轮次、协议错误上限、无进展和容量限制只产生 `AgentStopReason`，`AgentResult.failure()` 为空。只有循环内部的技术故障才同时返回 `FATAL_ERROR` 和非空 `AgentFailure`。未注册协议或动作、扩展返回 `null`、扩展篡改内核状态等均有明确 `AgentErrorCode`。

关键 Listener 的 `critical()` 只影响仍可推进的活动循环回调。`onError`、`onStop` 和 `afterRun` 是终态 best-effort 通知，Kernel 会忽略其异常，不能用它们阻止或改写已经决定的结果；需要可靠落库时应使用 `AgentCheckpointPort`。

## 外部观测

核心不依赖日志、指标或链路框架，通过 `AgentLifecycleListener` 提供固定观测端点。每个新回调都携带 `AgentLifecycleContext`，其中包含版本、`AgentLifecycleEventType`、`RUN/RESUME` 调用类型、唯一 `invocationId`、严格递增序号、`runId`、turn、零基 actionIndex、事件时间、deadline 和阶段耗时。模型、动作与检查点完成事件的耗时只覆盖对应扩展调用，轮次完成事件覆盖整轮处理，最终返回事件覆盖本次 `run/resume` 调用。`eventId()` 可作为同一次调用内的上报幂等键。

| 阶段 | 回调 | 主要数据 |
| --- | --- | --- |
| 调用与轮次开始 | `beforeRun`、`beforeTurn` | Request、RunState、调用身份 |
| 模型调用 | `beforeModel`、`afterModel` | ModelRequest、ModelResponse、调用耗时 |
| 决策与动作 | `afterDecision`、`beforeAction`、`afterAction` | Decision、Action、Result、actionIndex |
| 轮次完成 | `afterTurn` | Decision、全部 ActionResult、最新状态 |
| 协议与快照 | `onProtocolError`、`beforeCheckpoint`、`afterCheckpoint` | 协议扩展码、版本化 Snapshot |
| 终态 | `onError`、`onStop`、`afterRun` | 技术异常、停止原因、最终 AgentResult |
| 监听器健康 | `onListenerError` | 失败事件、Listener 类型、critical 标志和异常 |

模型适配器通过 `ModelCallMetrics` 返回 provider、model、requestId 和 Token 用量；供应商特有值继续放在其 attributes 中。Kernel 保证同步扩展调用已经返回后先发出完成事件；动作结果会先应用到状态，再判断调用后的取消或超时，避免已经发生的外部调用在观测和最终快照中消失。

Listener 在循环线程同步执行，耗时计入本次活动 deadline。普通日志、指标和 Trace Listener 应快速写入有界上报队列；独立健康 Listener 可通过 `onListenerError` 观测其他 Listener 的失败。需要阻止循环继续的关键投影才使用 `critical=true`。不得直接记录 Prompt、模型原文、工具参数、Observation 输出或请求属性，宿主必须按字段白名单脱敏和限长。

## 停止语义

`AgentStopController` 是所有循环出口的单一入口，依次处理：

1. 用户取消和线程中断。
2. 本次活动执行超时。
3. 完成或等待外部输入。
4. 最大轮次、连续协议错误和连续无进展。
5. 宿主注册的附加 `AgentStopPolicy`。

宿主策略只能追加停止条件，不能取消内核已经作出的停止决定。每个返回结果都携带 `AgentStopReason` 和最新 `AgentRunSnapshot`。

每次 `run` 或 `resume` 都有独立的执行起点和 deadline，暂停等待外部输入的时间不计入下一次恢复调用的活动执行超时。`AgentRunState.startedAt` 仍记录整个任务最初创建时间；任务总生命周期由宿主控制。

Model 和 Tool 是同步边界，内核不会为它们创建线程。适配器必须把 `AgentExecutionContext` 或 `AgentToolContext` 的 deadline 转换成底层 SDK/HTTP 超时，并主动响应 cancellation；内核会在模型和动作调用前后执行停止检查。该 deadline 是协作式约束：同步调用未返回时，核心不会强制中断线程，也不承诺立即返回。

`AgentLoopOptions` 同时限制每轮动作数量、工具批次大小、单工具时限和批次时限。容量超限以 `CAPACITY_LIMIT` 结束，不进入后续动作。工具 `callId` 在同一决策内必须唯一；确认、重试或恢复同一操作时必须复用原 `callId`。

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

注册只表示 Kernel 支持该协议；每次运行仍须通过请求显式选择：

```java
AgentRequest request = AgentRequest.of(
        "分析任务并返回结果",
        NativeFunctionCallingCodec.ID);
AgentResult result = kernel.run(request);
```

不同新任务可以选择不同协议，但同一任务运行中以及 `resume` 时不能切换协议。完整实现、版本规则和扩展动作选择见[协议扩展指南](docs/protocol-extension-guide.md)。

宿主可在启动时通过 `kernel.supportedProtocols()` 读取不可变协议 ID 列表，提前校验外部配置，禁止依赖异常消息判断协议是否存在。

内置 `agent-json:v1` 使用 `<<<AGENT_JSON>>>` / `<<<END_AGENT_JSON>>>` 严格包裹控制 JSON；无标记普通文本按最终回答处理。协议支持 `task_plan`、`tool_call`、`tool_batch`、`answer_chunk`、`final_answer` 和 `confirmation`，完整字段与错误码见[协议规范](docs/protocols/agent-json-v1.md)。

等待确认时，Kernel 将动作写入 `agent.pendingAction` 并以 `SUSPENDED` 返回。宿主恢复时通过唯一公开请求属性 `agent.confirmedActionIds` 传入确认 ID；内置 ReAct 只把这些 ID 注入模型上下文，不暴露其他宿主请求属性。相同 `confirmationId` 再次出现时会被消费为成功 Observation；确认消费、工具完成或失败以及最终回答都会清理 `agent.pendingAction`。

自定义协议可以映射到标准动作，也可以返回 `ExtensionAction`。自定义动作必须同时注册对应 `AgentActionHandler`；未注册动作会显式失败。

## 推理策略扩展

`AgentReasoningStrategy` 只负责生成本轮模型请求和校验语义动作。默认 `ReactReasoningStrategy` 的 planning、acting、evaluating、answering 阶段不会进入循环内核。

接入 Plan-and-Execute、Supervisor 或其他策略时，实现 `AgentReasoningStrategy` 并通过构建器替换即可，无需修改协议注册和停止控制。

## 中断与恢复

`AgentRunSnapshot` 不保存模型原始思维链，只保存轮次、标准观察、扩展属性、错误计数和停止结果。宿主负责读取快照，再显式恢复：

```java
AgentRunSnapshot snapshot = loadSnapshot(request.runId());
AgentResult result = kernel.resume(request, snapshot);
```

同一运行恢复时不能切换协议，防止旧快照被不同语义解释。

`resume` 只保证从已保存快照继续内核状态转换，不保证崩溃时未完成的外部副作用自动恢复，也不自动重放“已执行但尚未写入快照”的动作。宿主必须依靠稳定 `callId`、工具幂等结果和权威数据源判断重试或补偿。

`AgentCheckpointPort` 是只写快照端口。内核会在启动、协议错误、每个动作结果和停止时同时传入不可变请求与快照，并在写入前后发出固定观测事件；宿主可从请求属性取得 owner、fencing 等执行凭据，并负责快照查询、revision 及持久化条件写。外部工具副作用无法与快照存储形成通用原子事务，工具适配器必须使用 `AgentToolContext.callId` 作为幂等键；检查点写入失败会使本次运行以 `FAILED` 返回，不能继续推进后续动作。

会话单飞、任务排队、跨节点租约和“同一会话只能运行一个实例”属于宿主工作流职责，不进入单次任务循环内核。不同会话的资源上限由宿主提供的模型连接池和有界 Executor 负责。

## 非目标

核心只保证一次同步 `run/resume` 调用内的循环顺序、状态转换、停止优先级、容量边界和结构化失败。会话并发控制、数据库租约与 fencing、exactly-once、崩溃动作续跑、异步强制中止、业务补偿以及 RAG 检索、文档和权限模型均由宿主实现。

## 迁移说明

本轮直接替换了未发布的旧 API。原 `AgentReActExecutor`、`StateGraphWorkflowEngine`、Spring 自动装配以及项目/文档/代码仓库/权限/指标端口已移除。2026-10-09 进一步移除了核心主动查询快照的入口，并补齐 deadline-aware 模型/工具上下文和容量限制。需要上一版循环契约时可从 Git ref `db91ee5` 恢复；需要旧业务编排实现时可从 Git ref `a7a63f2` 恢复。
