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
| `id` | 全局 ID 生成 SPI 与可配置雪花实现 |

## 核心对象命名

Java API 使用以下固定术语，宿主适配器和扩展实现不应再引入同义别名：

| 领域 | 固定名称 | 说明 |
| --- | --- | --- |
| 运行结果 | `runStatus`、`stopDecision` | 当前运行状态与对应停止决定 |
| 停止决定 | `shouldStop`、`stopReason`、`runStatus`、`stopMessage` | 是否停止、原因、目标状态和说明 |
| 技术错误 | `errorCode`、`errorMessage` | 稳定错误分类与可读说明 |
| 动作 | `actionType`、`requestedRunStatus` | 协议无关的动作类型与 Handler 请求的运行状态；空状态表示继续 |
| 计划任务 | `taskId`、`dependsOn` | 任务身份与依赖任务 ID |
| 工具 | `toolName`、`callId`、`taskId`、`toolCalls` | 工具身份、单次操作身份、关联计划任务和批次调用集合 |
| 工具幂等 | `idempotencyKey`、`operationDefinition` | 前者是结构化 `(agentRunId, callId)` 持久化键，后者是可核对的操作内容 |
| 执行上下文 | `executionContext` | Model、Action、Tool、StopPolicy 共享的调用上下文 |
| 执行时限 | `invocationTimeout` | 单次 `run/resume` 活动调用的时限 |
| 扩展身份 | `protocolId`、`strategyId` | Codec 与推理策略的稳定身份 |
| 生命周期 | `agentRunId`、`invocationId`、`stepId`、`parentStepId`、`eventId`、`eventSequence`、`eventStatus` | 任务、单次调用、逻辑步骤、父步骤、事件、序号及事件状态 |

这些名称是 Java 内核对象契约。`agent-json:v1` 的 `type/taskId/dependsOn/toolName/callId/toolCalls` 等 wire 字段保持固定，由 Codec 显式映射，不因 Java 访问器整理而改变。

所有公开对象与字段的含义、所有者、空值、作用域和持久化边界统一见[对象与字段契约](docs/core-object-contract.md)；同名 `attributes`/`metadata` 不得跨层混用。

`agentRunId` 是 Java API、快照、宿主日志和持久化字段的唯一名称，不再使用 `runId` 别名。`sessionId` 标识会话，一个会话可以包含多个 Agent 任务；计划 `taskId` 标识计划内任务；`invocationId` 只标识一次 `run/resume` 调用。上述 ID 不可互换，尤其不能以 `sessionId` 或计划 `taskId` 代替工具幂等键中的 `agentRunId`。

### 核心专用对象

| 对象 | 唯一职责 | 不应承载 |
| --- | --- | --- |
| `AgentRequest` | 一次新运行或恢复调用的任务、协议、限制、属性和取消信号 | 快照加载、会话锁或宿主持久化状态 |
| `AgentRunState` | Kernel 内部当前状态的不可变值 | 宿主工作流对象或外部资源句柄 |
| `AgentRunSnapshot` | 可持久化、可恢复的版本化运行状态 | 未确认的外部副作用结果 |
| `AgentDecision` | Codec 输出的协议无关动作集合与协议 metadata | 模型供应商指标或执行追踪身份 |
| `AgentActionResult` | 一个动作产生的 Observation、状态变更，以及互斥的最终回答或暂停提示 | 未结构化的技术异常 |
| `AgentObservation` | 反馈给下一推理轮次的结构化动作结果 | Kernel 停止决定或宿主日志正文 |
| `AgentExecutionContext` | Kernel 生成的调用、步骤、deadline 与取消信封 | 业务协议字段或可由扩展覆盖的 ID |
| `AgentToolContext` | 工具操作身份、关联任务和工具 deadline | 单独作为持久化幂等键的 `callId` |
| `AgentStopDecision` | 是否停止、停止原因、目标运行状态和说明 | 技术故障详情对象；技术失败使用 `AgentFailure` |
| `AgentLifecycleContext` | 单条观测事件的身份、步骤、状态投影和时间信息 | 恢复权威状态或业务数据载荷 |

`AgentRunStatus` 表示运行状态；`AgentLifecycleContext.eventStatus()` 返回的是由事件类型推导的 `AgentLifecycleStatus`，仅用于步骤日志，二者不能混用。`AgentErrorCode`、`AgentStopReason`、`AgentLifecycleEventType`、`AgentLifecycleStatus` 和 `AgentLifecycleStepType` 均为稳定枚举，外部逻辑应按枚举分支，不应解析说明文本。

`AgentResult.answer` 只在 `COMPLETED` 时返回最终回答。`SUSPENDED` 时它为 `null`，等待提示由 `AgentResult.stopDecision().stopMessage()` 返回；快照 v3 独立保存 `suspensionMessage`。旧 v1/v2 快照不能直接按 v3 恢复，已有持久化数据需由宿主显式迁移。

## 最小接入

```java
AgentModel model = (request, context) ->
        ModelResponse.text(callYourModel(request, context.deadline()));

InMemoryAgentToolRegistry tools = new InMemoryAgentToolRegistry();
tools.register(yourTool);

AgentLoopKernel kernel = AgentLoopKernel.builder(model)
        .tools(tools)
        .toolExecutor(yourExecutor)
        .agentRunIdGenerator(new SnowflakeAgentRunIdGenerator(datacenterId, workerId))
        .build();

AgentRequest request = AgentRequest.of(
        "分析任务并返回结果",
        AgentJsonV1Codec.ID);
AgentResult result = kernel.run(request);
```

`agentRunId` 是一次可恢复 Agent 任务跨 `run/resume` 不变的 ID，上例在 Kernel 收到新请求后、发出第一条生命周期事件前生成雪花 ID；`datacenterId` 和 `workerId` 范围均为 0–31，宿主必须保证每个并发进程使用唯一组合，禁止所有实例沿用相同默认值。业务已有专用于该 Agent 任务的唯一 ID 时也可直接传入 `AgentRequest`，此时 Kernel 不调用生成器；不能直接复用会话 ID 或计划任务 ID。恢复请求必须显式传入原快照 `agentRunId`，绝不生成新 ID。生成器异常或空结果分别以稳定的 `ID_GENERATION_FAILED` 返回。

构建器默认使用同步 `Executor`，不会创建或持有线程。需要工具并行执行时，宿主必须通过 `toolExecutor` 提供有界线程池并负责其生命周期。构建器仅用于启动期组装；`build()` 会冻结工具集合，之后修改原工具注册表不会影响已创建的 Kernel。

模型调用默认最多尝试 3 次（首次调用加 2 次重试），仅当模型适配器抛出 `AgentCoreException(AgentErrorCode.MODEL_CALL_FAILED, ..., true, cause)` 时重试。普通异常、`retryable=false`、协议错误和工具错误不会自动重试。可在构建时通过 `modelRetry(new AgentModelRetryOptions(maxAttempts, initialDelay, maxDelay))` 调整次数与退避范围；`maxAttempts` 限 1–10，退避范围限 1 ms–30 s，`maxAttempts=1` 可关闭重试。等待采用有上限的指数退避与抖动，计入本次 `run/resume` 的活动 deadline；取消、线程中断或超时后不会开始下一次模型调用。整个过程仍属于同一个 MODEL step，步骤耗时包含重试等待与全部模型调用。

`AgentLoopKernel` 可以作为单例供多个独立任务并发调用，但注入的 IdGenerator、Model、Strategy、Codec、Handler、Tool、StopPolicy、CheckpointPort 和 Listener 必须线程安全。进入公开数据载体的 Map、List、Set 和数组会被递归快照为只读结构，数组统一规范化为 List；不透明业务 DTO 不做反射复制，必须不可变或按调用独立创建。每个活动请求应使用独立的 `AgentCancellation`；复用同一个可变取消探针会按设计同时取消这些请求。

状态属性有明确所有权：`agent.*` 属于 Kernel，`react.*` 属于内置 ReAct；自定义 Action Handler 不得写入或删除这两个保留命名空间。`AgentRunState` 的启动、恢复、轮次推进、动作应用与停止转换仅由 Kernel 调用，对外只提供状态读取、快照投影以及 Strategy 所需的 `withAttributes`。自定义 Strategy 的 `strategyId()` 必须非空且不能占用 `agent` 命名空间，`afterTurn` 只能修改 `{strategyId}.*`。违反边界时运行返回 `EXTENSION_CONTRACT_VIOLATION`。`AgentActionResult.removedStateAttributes` 显式表达删除，Kernel 总是先删除再合并新值。

## 对外结果与错误契约

核心把“为何停止”和“哪里出错”分开表达：

| 类型 | 用途 | 调用方处理方式 |
| --- | --- | --- |
| `AgentStopReason` | 每次循环退出的稳定原因，包括完成、等待输入、结果核验、取消、超时和容量限制 | 始终读取 `AgentResult.stopDecision()` |
| `AgentErrorCode` | 请求、配置、快照、协议、扩展及技术故障的稳定分类 | 禁止依赖异常消息做分支 |
| `AgentCoreException` | `run/resume` 前置校验和 Kernel 组装失败 | 读取 `errorCode()` 与 `retryable()` |
| `AgentProtocolException` | Codec 或 Strategy 报告的可纠正协议错误，`protocolErrorCode()` 提供协议扩展码 | Kernel 计入协议错误次数并继续，达到上限后停止 |
| `AgentFailure` | 活动循环中的 Model、Strategy、Codec、Action、Checkpoint 或关键 Listener 技术失败 | 先读取 `stopDecision()`，`failure()` 非空时按技术故障处理 |
| `AgentObservation.errorCode` | 单个工具调用的结构化错误，如未找到、取消、超时、结果未知或执行失败 | 明确失败由 Strategy 处理，结果未知由 Kernel 强制进入核验暂停 |

正常完成、等待输入、结果核验、取消、活动超时、最大轮次、协议错误上限、无进展和容量限制只产生 `AgentStopReason`，`AgentResult.failure()` 为空。只有循环内部的技术故障才同时返回 `FATAL_ERROR` 和非空 `AgentFailure`。未注册协议或动作、扩展返回 `null`、扩展篡改内核状态等均有明确 `AgentErrorCode`。

关键 Listener 的 `critical()` 只影响仍可推进的活动循环回调。`onError`、`onStop` 和 `afterRun` 是终态 best-effort 通知，Kernel 会忽略其异常，不能用它们阻止或改写已经决定的结果；需要可靠落库时应使用 `AgentCheckpointPort`。

## 外部观测

核心不依赖日志、指标或链路框架，通过 `AgentLifecycleListener` 提供固定观测端点。每条事件都先进入统一 `onEvent`，并携带版本、事件类型、状态、`agentRunId`、`invocationId`、`stepId`、`parentStepId`、`eventId`、`stepType`、`operation`、严格递增序号、turn、零基 actionIndex、事件时间、deadline 和阶段耗时。`agentRunId` 关联整个任务，`invocationId` 关联一次 `run/resume`，同一模型、决策、动作或检查点的 started/completed/failed 事件共享 `stepId`，单条消息使用 `eventId()` 幂等上报。模型、决策或动作异常与提前停止分别发出对应的 `MODEL_FAILED`、`DECISION_FAILED`、`ACTION_FAILED`，未完成的轮次发出 `TURN_FAILED`；`RUN_FAILED/RUN_STOPPED/RUN_RETURNED` 始终归属 run step。`AgentExecutionContext` 固定携带同一组 invocation/step 身份：Model Adapter 获得 model step，Action Handler 及其 Tool Adapter 获得 action step，StopPolicy 获得检查发生时的当前 step；适配器内部日志可直接与 Listener 最终步骤日志关联。模型完成事件的耗时覆盖该步骤的全部尝试与退避等待；动作与检查点完成事件只覆盖对应扩展调用，轮次完成事件覆盖整轮处理，最终返回事件覆盖本次调用。检查点失败会额外发出 `CHECKPOINT_FAILED` 并调用 best-effort `onCheckpointError`。

完整步骤日志优先实现一个非关键 Listener 的 `onEvent`。宿主日志字段使用 `agentRunId`，取值来自 `event.agentRunId()`：

```java
AgentLifecycleListener stepLogger = new AgentLifecycleListener() {
    @Override
    public void onEvent(AgentLifecycleContext event) {
        String agentRunId = event.agentRunId();
        writeStepLog(agentRunId, event.invocationId(), event.stepId(),
                event.parentStepId(), event.eventId(), event.eventType(), event.eventStatus(),
                event.stepType(), event.operation(), event.turn(), event.actionIndex(),
                event.elapsed());
    }
};
```

所有步骤都会输出对应操作：run/resume、turn、model、decision、标准或扩展 Action、checkpoint。工具 Action 通过 `beforeAction/afterAction` 额外投影 `callId/taskId/toolName/success/errorCode`；模型通过 `afterModel` 投影 provider/model/requestId/Token 用量；终态通过 `onStop/onError/afterRun` 输出停止原因或失败码。禁止记录未脱敏 Prompt、模型原文、工具 arguments、Observation output 和请求属性。

| 阶段 | 回调 | 主要数据 |
| --- | --- | --- |
| 调用与轮次开始 | `beforeRun`、`beforeTurn` | Request、RunState、调用身份 |
| 模型调用 | `beforeModel`、`afterModel` | ModelRequest、ModelResponse、调用耗时 |
| 决策与动作 | `beforeDecision`、`afterDecision`、`beforeAction`、`afterAction` | Decision、Action、Result、stepId、actionIndex |
| 轮次终态 | `afterTurn`、`onTurnError` | Decision、全部 ActionResult、协议错误和最新状态 |
| 协议与快照 | `onProtocolError`、`beforeCheckpoint`、`afterCheckpoint`、`onCheckpointError` | 协议扩展码、版本化 Snapshot、持久化失败 |
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

宿主策略只能追加停止条件，不能取消内核已经作出的停止决定，也不能返回 `COMPLETED` 代替最终回答动作；违反该约束会以 `EXTENSION_CONTRACT_VIOLATION` 结束。每个返回结果都携带 `AgentStopReason` 和最新 `AgentRunSnapshot`。

每次 `run` 或 `resume` 都有独立的执行起点和 deadline，暂停等待外部输入的时间不计入下一次恢复调用的活动执行超时。`AgentRunState.startedAt` 仍记录整个任务最初创建时间；任务总生命周期由宿主控制。

Model 和 Tool 是同步边界，内核不会为它们创建线程。适配器必须把 `AgentExecutionContext` 或 `AgentToolContext` 的 deadline 转换成底层 SDK/HTTP 超时，并主动响应 cancellation；记录底层请求日志时必须沿用其中的 `agentRunId/invocationId/stepId`，工具再追加 `callId/taskId/toolName`。内核会在模型和动作调用前后执行停止检查。该 deadline 是协作式约束：同步调用未返回时，核心不会强制中断线程，也不承诺立即返回。

`AgentLoopOptions` 同时限制每轮动作数量、工具批次大小、单工具时限和批次时限。容量超限以 `CAPACITY_LIMIT` 结束，不进入后续动作。工具 `callId` 在同一决策内由 Kernel 判重，并且在整个 run 内只能标识一个工具操作；Kernel 会把 `callId/taskId/toolName/arguments` 身份写入快照账本，跨轮复用同 ID 但更换身份时以可纠正的 `TOOL_IDEMPOTENCY_CONFLICT` 拒绝。后续轮次的新操作必须使用新 ID，只有确认、重试、核验或恢复同一操作时才复用原 ID。工具持久化幂等身份固定为结构化 `AgentToolIdempotencyKey(agentRunId, callId)`，禁止单独使用 `callId` 或用无边界字符串拼接替代结构化字段。适配器仍必须保存 `toolName + 规范化 arguments` 参数指纹，作为跨进程和外部副作用的最终防线：同身份同指纹返回原结果，同身份异指纹返回 `TOOL_IDEMPOTENCY_CONFLICT`。每条标准工具 Observation 固定携带 `callId`、可选 `taskId` 和 `toolName`，工具自定义 attributes 不能覆盖这些身份字段。

标准 `tool_batch` 使用宿主提供的 Executor 并发执行。只要已有任务提交后发生超时、线程中断或提交/汇总异常，Kernel 就将结果标记为 `TOOL_RESULT_UNKNOWN`、保留完整 `agent.pendingAction`，并以 `RECONCILIATION_REQUIRED + SUSPENDED` 停止本次调用。工具适配器主动返回 `ToolExecutionResult.failure(TOOL_RESULT_UNKNOWN, ...)` 时采用相同出口：单工具保存原调用，批次任一调用未知则保存完整批次及全部 Observation。该停止优先于同一批次等待期间发生的通用超时或线程中断，避免未知副作用被覆盖为不可恢复终态；线程中断标志仍会保留。宿主恢复前必须按每个 `AgentToolIdempotencyKey` 查询权威结果；带依赖关系的批次仍应使用自定义 Action/Handler。

`RECONCILIATION_REQUIRED` 快照禁止空输入恢复。宿主必须为 pending 中每个调用提供一条权威 `tool_call` Observation，并通过 `AgentResumeInput` 调用三参数 `resume`；Kernel 会校验 `callId/taskId/toolName` 完整匹配，拒绝缺项、重复项、非 pending 调用和仍为 `TOOL_RESULT_UNKNOWN` 的结果。结构无效返回 `INVALID_RESUME_INPUT`，身份不匹配返回 `PENDING_ACTION_MISMATCH`，校验成功后 Kernel 清理 pending、保存新快照并进入下一模型轮次。

工具适配器必须区分“明确执行失败”和“可能已产生副作用但响应未知”。前者返回具体失败码并允许 Strategy 决定下一步；后者必须返回 `TOOL_RESULT_UNKNOWN`，不得仅抛普通运行时异常或降级成 `TOOL_EXECUTION_FAILED`。

## 协议扩展

`AgentProtocolCodec` 负责两个方向：

- 在模型调用前补充当前协议说明。
- 将模型响应转换为协议无关的 `AgentDecision`。

新增协议只需实现 Codec 并注册：

```java
AgentLoopKernel kernel = AgentLoopKernel.builder(model)
        .protocol(new NativeFunctionCallingCodec())
        .agentRunIdGenerator(new SnowflakeAgentRunIdGenerator(datacenterId, workerId))
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

宿主可在启动时通过 `kernel.supportedProtocolIds()` 读取不可变协议 ID 列表，提前校验外部配置，禁止依赖异常消息判断协议是否存在。

内置 `agent-json:v1` 使用 `<<<AGENT_JSON>>>` / `<<<END_AGENT_JSON>>>` 严格包裹控制 JSON；无标记普通文本按最终回答处理。协议支持 `task_plan`、`tool_call`、`tool_batch`、`answer_chunk`、`final_answer` 和 `confirmation`，完整字段与错误码见[协议规范](docs/protocols/agent-json-v1.md)。

等待批准时，Kernel 将动作写入 `agent.pendingAction` 并以 `SUSPENDED` 返回。宿主恢复确认动作时通过 `agent.approvedConfirmationIds` 传入 `confirmationId`，恢复危险工具时通过 `agent.approvedToolCallIds` 传入 `callId`；缺少对应批准 ID 会在模型调用前以 `INVALID_RESUME_INPUT` 拒绝。批准后，模型本轮的首个动作必须重述同一待处理动作，危险工具须完整匹配 `callId/taskId/toolName/arguments`；批次可包含待批准的原工具调用并继续逐项批准。不匹配计入可纠正的 `PENDING_ACTION_MISMATCH` 协议错误，不允许其他动作清理 pending。新 run 预置批准 ID 不能绕过暂停。内置 ReAct 只把这两类公开批准 ID 注入模型上下文，不暴露其他宿主请求属性和内部操作账本。相同且已批准的操作可安全重试；批准消费、工具完成或失败以及最终回答都会清理 `agent.pendingAction`。

自定义协议可以映射到标准动作，也可以返回 `ExtensionAction`。自定义动作必须同时注册对应 `AgentActionHandler`；未注册动作会显式失败。只有标准 `ToolCallAction` 和 `ToolBatchAction` 的 `TOOL_RESULT_UNKNOWN` 会触发内置 `RECONCILIATION_REQUIRED`；扩展动作应使用自己的 pending 状态和恢复协议，其未知结果不会被误解释为标准工具核验。

## 推理策略扩展

`AgentReasoningStrategy` 只负责生成本轮模型请求和校验语义动作。默认 `ReactReasoningStrategy` 的 planning、acting、evaluating、answering 阶段不会进入循环内核。

接入 Plan-and-Execute、Supervisor 或其他策略时，实现 `AgentReasoningStrategy` 并通过构建器替换即可，无需修改协议注册和停止控制。

## 中断与恢复

`AgentRunSnapshot` v3 不保存模型原始思维链，只保存轮次、标准观察、扩展属性、错误计数、最终回答或暂停提示以及停止结果。宿主负责读取快照，再显式恢复：

```java
String agentRunId = request.agentRunId();
AgentRunSnapshot snapshot = loadSnapshot(agentRunId);
AgentResult result = kernel.resume(request, snapshot);
```

结果核验暂停必须注入权威结果：

```java
AgentObservation observation = AgentObservation.toolResult(
        callId, taskId, toolName, authoritativeToolResult);
AgentResumeInput input = new AgentResumeInput(List.of(observation));
AgentResult result = kernel.resume(request, snapshot, input);
```

同一运行恢复时不能切换协议，防止旧快照被不同语义解释。

`resume` 只保证从已保存快照继续内核状态转换，不保证崩溃时未完成的外部副作用自动恢复，也不自动重放“已执行但尚未写入快照”的动作。对于 Kernel 已经以 `RECONCILIATION_REQUIRED` 暂停的标准工具动作，宿主必须依靠稳定 `callId` 和权威数据源核验，并通过 `AgentResumeInput` 注入确定结果；其他崩溃恢复、重试和补偿仍由宿主处理。

`AgentCheckpointPort` 是只写快照端口。内核会在启动、协议错误、每个动作结果和停止时同时传入不可变请求与快照，并在写入前后发出固定观测事件；宿主可从请求属性取得 owner、fencing 等执行凭据，并负责快照查询、revision 及持久化条件写。外部工具副作用无法与快照存储形成通用原子事务，工具适配器必须使用 `AgentToolContext.idempotencyKey()` 及参数指纹保证幂等；检查点写入失败会发出 `CHECKPOINT_FAILED/onCheckpointError`，并使活动运行以 `FAILED` 返回，不能继续推进后续动作。即使取消、线程中断或超时已触发，若保存对应终态快照再次失败，`run/resume` 仍返回 `FATAL_ERROR + CHECKPOINT_FAILED`，不会把持久化失败作为未捕获异常抛出。处理其他技术故障时若失败快照也无法保存，返回的 `AgentFailure` 优先报告 `CHECKPOINT_FAILED`，原始异常保留为 suppressed cause；宿主不得将返回快照视为已持久化。

会话单飞、任务排队、跨节点租约和“同一会话只能运行一个实例”属于宿主工作流职责，不进入单次任务循环内核。不同会话的资源上限由宿主提供的模型连接池和有界 Executor 负责。

## 非目标

核心只保证一次同步 `run/resume` 调用内的循环顺序、状态转换、停止优先级、容量边界和结构化失败。Kernel 的 RunState、生命周期 eventSequence、invocationId、deadline 和临时集合都是调用局部变量，不会在不同调用间共享；同一个 Kernel 可以并发执行不同 run。共享 Model、Strategy、Codec、Handler、Tool、StopPolicy、CheckpointPort、Listener、Clock 和 Executor 必须线程安全；结构化容器由 core 递归快照，不透明业务 DTO 仍须不可变或按调用隔离。会话并发控制、数据库租约与 fencing、exactly-once、崩溃动作续跑、异步强制中止、业务补偿以及 RAG 检索、文档和权限模型均由宿主实现。

## 迁移说明

本轮直接替换了未发布的旧 API。原 `AgentReActExecutor`、`StateGraphWorkflowEngine`、Spring 自动装配以及项目/文档/代码仓库/权限/指标端口已移除。2026-10-09 进一步移除了核心主动查询快照的入口，并补齐 deadline-aware 模型/工具上下文和容量限制。需要上一版循环契约时可从 Git ref `db91ee5` 恢复；需要旧业务编排实现时可从 Git ref `a7a63f2` 恢复。
