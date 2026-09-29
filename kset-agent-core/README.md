# kset-agent-core

Agent 编排能力公共组件：ReAct 编排执行器、工具调度与注册、工作流引擎（任务持久化/异步执行/检查点恢复）、护栏/成本/输出转换扩展点，以及面向宿主应用的全量 SPI 端口。

## 引入

```xml
<dependency>
    <groupId>com.kset</groupId>
    <artifactId>kset-agent-core</artifactId>
</dependency>
```

自动装配入口：`com.kset.agent.core.autoconfigure.KsetAgentAutoConfiguration`（`com.kset.agent.core` 组件扫描 + SPI 默认实现）。

## 包结构

| 包 | 内容 |
|----|------|
| `com.kset.agent.core` | AgentReActExecutor、AgentProtocolDefinition、AgentToolActionDispatcher、提示词构建/记忆优化/输出预算 |
| `com.kset.agent.core.workflow` | WorkflowEngine、WorkflowTask/Step、WorkflowTaskRepository、WorkflowRuntimeStateStore、状态与确认枚举 |
| `com.kset.agent.core.tool` | ToolDefinition、ToolRegistry、InMemoryToolRegistry、ToolEntryPermissionPolicy |
| `com.kset.agent.core.memory` | 记忆上下文与优先级/类型 |
| `com.kset.agent.core.model` | AiCallDimension |
| `com.kset.agent.core.context` / `stream` | 执行上下文、调用元数据、流式输出上下文 |
| `com.kset.agent.core.extension.*` | guardrail / cost / output / workflow 扩展点接口（output 含协议解析默认实现，其余由业务侧实现） |
| `com.kset.agent.core.port` | 指标与查询端口（no-op 默认实现） |
| `com.kset.agent.core.engine` | StateGraphWorkflowEngine（状态存储由宿主实现 WorkflowRuntimeStateStore） |
| `com.kset.agent.core.spi` | 宿主应用接入端口（见下） |
| `com.kset.agent.core.config` | AgentOrchestrationProperties（`ai.agent.orchestration.*`） |

## 宿主应用必须提供的 Bean

| Bean | 说明 |
|------|------|
| `com.kset.agent.core.spi.AgentModelPort` | 模型接入：调用（`chatWithSystem` / 降级判定）+ 能力/预算描述（均有默认值，可按需覆盖） |
| `com.kset.agent.core.workflow.WorkflowTaskRepository` | 工作流任务/步骤持久化（含抢占与 fencing） |
| `com.kset.agent.core.workflow.WorkflowRuntimeStateStore` | 工作流运行时状态存储（确认态/取消标记，宿主自行选择 JDBC/Redis 等实现） |
| 名为 `agentToolTaskExecutor` 的 `Executor` | 工具并行执行线程池 |

启动时由 `AgentRequiredBeanVerifier` 对上述 Bean 做 fail-fast 校验，缺失即抛出携带接入指引的异常；可用 `ai.agent.required-bean-check=false` 关闭校验。

## 可覆盖的 SPI（均有默认实现）

| 端口 | 默认行为 |
|------|----------|
| `AgentRuntimeConfigPort` | 从 Spring Environment 读取 |
| `AgentMessagePort` | 返回文案编码本身，语言策略 `auto` |
| `ContentSecurityPort` | 不清洗、不拦截 |
| `DocumentAccessPort` | 返回 `null`（不做文档级过滤） |
| `ModelPricingPort` / `IndexedProjectScopePort` / `CodeRepositoryAccessPort` | 空列表 |
| `ProjectAccessPort` | 宽松放行（无项目体系场景） |
| `ToolRegistry` | InMemoryToolRegistry |
| `ToolCallMetricsPort` / `AgentWorkflowMetricsPort` / `AiFlowMetricsPort` | no-op |
| `LlmHealthPort` | 不降级、空快照 |
| `AiSessionQualityQueryPort` | 空列表 |

## 安全上下文

`AgentAuthContext`（`com.kset.agent.core.spi`）为线程级安全上下文：宿主在请求边界 `setCurrentUser` / `setRequestAccessScope`，结束必须 `clear()`；执行器在异步工具线程内自动重放会话快照。

## 配置

- `ai.agent.orchestration.*`：max-steps、max-parallel-tools、重试上限、工具批次超时等（见 `AgentOrchestrationProperties`）
  - `ai.agent.orchestration.identity`：Agent 在系统提示词中的身份称谓（默认 `AI 助手`），仅影响提示词身份描述，不影响协议
  - `ai.agent.orchestration.tool-entry-permission`：Agent/MCP 工具入口统一校验的功能权限点；留空（默认）不校验
- `ai.agent.required-bean-check`：宿主必配 Bean 的 fail-fast 校验开关，默认 `true`
- `ai.workflow.execution.lease-seconds` / `timeout-seconds`

## 协议

- LLM 输出协议：`agent-json-v1`（`AgentProtocolDefinition` 为单一事实源），控制动作用 `<<<AGENT_JSON>>>` 与 `<<<END_AGENT_JSON>>>` 包裹，支持六种动作：task_plan / tool_call / tool_batch / answer_chunk / final_answer / confirmation；解析走 `AgentOutputConverter` SPI（默认 `JsonAgentOutputConverter`）
- 流式事件：`ChatStreamEvent`（step / task / status / done / error），由 `ChatStreamContext` 线程级发射
- 错误码契约：`AgentWorkflowErrorCode`（工作流/模型/协议/工具/确认等类别，含默认文案）

## 来源与同步说明

代码同步自 kset-rag 项目 `kset-rag-agent` 模块的编排核心（包名 `com.kset.rag.*` → `com.kset.agent.core.*`），业务耦合点已抽象为 `com.kset.agent.core.spi` 端口。模块只保留最核心逻辑：ReAct 编排、agent-json-v1 协议、结果模型与工作流引擎；代码沙箱、状态存储（JDBC/Redis）、护栏/成本适配器等默认实现已移除，由业务侧自行实现对应 SPI。kset-rag 侧暂未切换到本模块，后续切换时需为各 SPI 提供适配实现并删除已迁移类。
