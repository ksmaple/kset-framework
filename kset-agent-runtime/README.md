# kset-agent-runtime

通用 Agent 组装层。依赖方向为业务应用 → Runtime → Core。Runtime 为每次 `run/resume` 固定工具目录，然后调用 Core 的同步循环；它不持有工作流状态，也不实现第二套循环。

## 引入

```xml
<dependency>
    <groupId>com.kset</groupId>
    <artifactId>kset-agent-runtime</artifactId>
</dependency>
```

模块使用 Java 21，仅依赖 `kset-agent-core`；不提供 Spring 自动装配、Web 接口、数据库存储或 MCP 传输实现。对象字段与空值边界见[Runtime 对象与字段契约](docs/runtime-object-contract.md)。

## 组装与调用

```java
AgentRuntime runtime = AgentRuntime.builder(model)
        .tool(readDocumentTool)
        .tool(writeDescriptor, (arguments, toolContext) ->
                writeDocument(arguments, AgentRuntimeContexts.require(toolContext)))
        .skill("code-search", context -> codeSearchToolsFor(context))
        .protocol(ragCodeV7Codec)
        .strategy(ragStrategy)
        .checkpoints(checkpointPort)
        .onEvent(event -> publishEvent(event))
        .onModelMetrics((event, metrics) -> recordModelUsage(event, metrics))
        .build();

AgentBusinessContext businessContext = new AgentBusinessContext(
        currentUserId, currentTenantId, Set.of("reviewer"),
        Map.of("submittedContent", submittedContent));
AgentResult result = runtime.run(coreRequest, businessContext, List.of("code-search"));
```

`readDocumentTool`、`writeDocument` 与 `codeSearchToolsFor` 由业务侧实现。`.tool(AgentTool)` 直接注册已有工具；`.tool(descriptor, handler)` 将工具描述和执行函数组合成工具。`.skill(id, source)` 按本次上下文生成具名工具集合，调用时按 ID 选择。相同工具名不能出现在多个选中的工具源中。策略和协议仍由 Core SPI 提供。默认 ReAct 只看到选中工具的描述，不会自动读取 `submittedContent` 或角色。需要让模型看到某些业务内容时，由业务策略明确选择和脱敏后放入 Prompt。

原有 `.tools(source/registry)`、`.skill(AgentSkill)`、`.listener(AgentLifecycleListener)` 继续可用。已有按次构建的 Core `AgentToolRegistry` 可直接传入 `.tools(registry)`；Runtime 会在每次调用时读取并固定其工具目录。

`.onEvent(callback)` 接收 Core 的生命周期事件上下文；`.onModelMetrics(callback)` 只在模型响应提供非空 `ModelCallMetrics` 时调用，参数为该次 `MODEL_COMPLETED` 上下文和指标。回调同步执行，遵循普通非关键 Listener 的失败处理；终态通知为 best-effort，可靠状态仍须使用 CheckpointPort。回调应保持轻量、线程安全，不能直接记录未脱敏的请求内容。

## 生命周期耗时日志

Runtime 默认注册 Core 提供的无状态 `AgentTimingLogListener`，复用生命周期事件的 `elapsed`，通过 Java 平台日志输出稳定关键词 `agent.timing`。模型步骤达到 30 秒、`tool_call` 或 `tool_batch` 动作达到 10 秒时，该步骤日志标记 `slow=true` 并输出 WARNING；批次按整个动作耗时判断。其余整次 `run/resume` 的终态和模型调用耗时为 INFO；模型、动作或检查点失败为 WARNING；动作、轮次和检查点的逐步耗时为 DEBUG。宿主可按 `com.kset.agent.core.event.AgentTimingLogListener` 调整日志等级；直接使用 Core 的宿主也可通过 `.listener(new AgentTimingLogListener())` 注册。

日志包含 `operation`、阶段、状态、`agentRunId`、`invocationId`、`stepId`、轮次与 `durationMs`。整次调用使用 `runStatus`，步骤使用 `eventStatus`；动作的 `COMPLETED` 只表示该动作调用已返回，不等于工具业务成功。模型耗时包含 Core 的重试等待，整次调用耗时只覆盖 Core 执行，不包含 Runtime 的工具目录组装。日志不记录 Prompt、模型正文、工具参数或 Observation。

需要按身份、租户或业务配置为每次调用选择模型时，可配置 `.modelFactory(context -> selectModel(context))`；未配置时使用 `builder(model)` 的默认模型。需要按工具目录构建策略时，可配置 `.strategyFactory(tools -> createStrategy(tools))`。共享的模型、策略、Codec、工具、检查点和监听器必须符合 Core 的线程安全要求；持有可变单次状态的组件应为每次调用创建独立 Runtime。

恢复时使用相同任务与协议、快照中的 `agentRunId`，并重新提供当前可信的 `AgentBusinessContext` 与所选 Skill；需要核验未知工具结果时，传入 Core 的 `AgentResumeInput`：

```java
AgentResult resumed = runtime.resume(coreRequest, snapshot, resumeInput,
        currentBusinessContext, List.of("code-search"));
```

业务工具从工具上下文读取本次身份与内容：

```java
AgentBusinessContext business = AgentRuntimeContexts.require(toolContext);
String principalId = business.principalId();
Set<String> roles = business.roles();
Object submittedContent = business.attributes().get("submittedContent");
```

`AgentBusinessContext` 只在这次调用的请求与执行上下文中传递，不进入 Core 快照。它也可由模型适配器从 `AgentExecutionContext` 读取，但不会自动成为模型输入。请求不能自行占用 Runtime 的上下文属性键。跨线程工具无需依赖 ThreadLocal；Core 将同一执行上下文传给工具。其他业务 ThreadLocal 的传播由宿主执行器负责。

自定义策略可用 `AgentRuntimeContexts.require(request)` 读取同一上下文；策略必须明确选择哪些字段进入 Prompt，并处理敏感内容与长度限制。

## MCP 工具

业务侧实现 `McpAgentClient` 的 `listTools` 和 `call`，并通过 `.mcpTools(client)` 加入 Runtime。`McpAgentToolSource` 把本次返回的 `AgentToolDescriptor` 转为 Core 工具；MCP 连接、凭据、超时和取消传递由客户端实现负责。MCP 服务应在每次 `call` 时按当前身份、资源与操作检查权限。

运行时的工具筛选只控制模型能看到的工具集合，不构成权限授予。工具确认只表示交互决定，不代替授权。业务工具和 MCP 服务每次真实执行都应校验当前身份与权限；缺少可信主体时，业务侧应拒绝调用。写工具必须按 Core 的 `(agentRunId, callId)` 保存工具名和参数指纹、处理重试与未知结果；Runtime 不提供业务幂等存储。

## kset-rag 接入

`kset-rag` 的 `KsetRagCoreExecutor` 使用 Runtime 组装现有每轮 `AgentModel`、`code-v7` Codec、Strategy、Handler、Checkpoint、Listener 和 `KsetRagCoreTools.forRun(...)` 工具注册表。原有 Prompt、模型提供方、记忆、代码索引、SSE、工作流任务状态、权限校验与业务工具仍归 `kset-rag`。带依赖关系的工具批次仍使用原扩展 Handler。业务侧须显式依赖同版本 Runtime 构件，并在构建前确保 Maven 仓库可解析。
