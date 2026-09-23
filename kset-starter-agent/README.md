# kset-starter-agent

Agent 编排能力公共组件：ReAct 编排执行器、工具调度与注册、工作流引擎（任务持久化/异步执行/检查点恢复）、护栏/成本/输出转换扩展点，以及面向宿主应用的全量 SPI 端口。

## 引入

```xml
<dependency>
    <groupId>com.kset</groupId>
    <artifactId>kset-starter-agent</artifactId>
</dependency>
```

自动装配入口：`com.kset.agent.autoconfigure.KsetAgentAutoConfiguration`（`com.kset.agent` 组件扫描 + SPI 默认实现）。

## 包结构

| 包 | 内容 |
|----|------|
| `com.kset.agent.core` | AgentReActExecutor、AgentProtocolDefinition、AgentToolActionDispatcher、提示词构建/记忆优化/输出预算 |
| `com.kset.agent.workflow` | WorkflowEngine、WorkflowTask/Step、WorkflowTaskRepository、状态与确认枚举 |
| `com.kset.agent.tool` | ToolDefinition、ToolRegistry、InMemoryToolRegistry、ToolEntryPermissionPolicy |
| `com.kset.agent.memory` | 记忆上下文与优先级/类型 |
| `com.kset.agent.model` | AiModelProvider、ToolSpec、ImageContent、AiCallDimension |
| `com.kset.agent.context` / `stream` | 执行上下文、调用元数据、流式输出上下文 |
| `com.kset.agent.extension.*` | guardrail / cost / output / workflow 扩展点 |
| `com.kset.agent.port` | 指标与查询端口（no-op 默认实现） |
| `com.kset.agent.engine` | StateGraphWorkflowEngine、Jdbc/Redis WorkflowRuntimeStateStore |
| `com.kset.agent.sandbox` | 代码沙箱（JVM 受限执行） |
| `com.kset.agent.spi` | 宿主应用接入端口（见下） |
| `com.kset.agent.config` | AgentOrchestrationProperties（`ai.agent.orchestration.*`） |

## 宿主应用必须提供的 Bean

| Bean | 说明 |
|------|------|
| `com.kset.agent.spi.AgentChatModelPort` | 模型调用（`chatWithSystem` / 降级判定 / 激活模型描述） |
| `com.kset.agent.model.AiModelProvider` | 模型能力描述（输出预算计算） |
| `com.kset.agent.workflow.WorkflowTaskRepository` | 工作流任务/步骤持久化（含抢占与 fencing） |
| 名为 `agentToolTaskExecutor` 的 `Executor` | 工具并行执行线程池 |

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

## 安全上下文

`AgentAuthContext`（`com.kset.agent.spi`）为线程级安全上下文：宿主在请求边界 `setCurrentUser` / `setRequestAccessScope`，结束必须 `clear()`；执行器在异步工具线程内自动重放会话快照。

## 配置

- `ai.agent.orchestration.*`：max-steps、max-parallel-tools、重试上限、工具批次超时等（见 `AgentOrchestrationProperties`）
- `ai.workflow.state.store`：`database`（默认，Jdbc）/ `redis`
- `ai.workflow.execution.lease-seconds` / `timeout-seconds`
- `ai.tool.sandbox.enabled`：默认开启 JVM 受限沙箱

## 来源与同步说明

代码同步自 kset-rag 项目 `kset-rag-agent` 模块的编排核心（包名 `com.kset.rag.*` → `com.kset.agent.*`），业务耦合点已抽象为 `com.kset.agent.spi` 端口。kset-rag 侧暂未切换到本 starter，后续切换时需为各 SPI 提供适配实现并删除已迁移类。
