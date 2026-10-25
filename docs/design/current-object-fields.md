# 当前代码对象与字段定义

> 基线：当前主工作树源码（含本地未提交修改），整理日期：2026-10-25。
> 范围：所有模块的公开 record、配置属性类及命名为 Request/Response/Result/Context/Options/Spec/Snapshot 等的公开数据类；包含配置类的嵌套属性对象。
> 下表记录源码声明的字段类型、初始化表达式与就近注释，并链接所属源码文件。`—` 表示源码未声明初始化值或注释，不代表运行时一定为 null。配置绑定、构造器校验与派生方法可能改变最终值，须以链接源码为准。

共 120 个对象、590 个字段；公开接口方法、枚举取值、常量、服务内部状态及业务使用方表字段不计入字段数。`kset-boot-parent` 是构建期父 POM；`kset-starter-gateway`、`kset-starter-mq`、`kset-starter-nacos`、`kset-starter-sentinel` 在上述筛选范围内没有自有字段对象，分别通过共享配置、第三方配置、SPI 或动态结构承载数据。

## 跨模块对象关系

| 对象或对象组 | 字段语义与边界 | 来源 |
| --- | --- | --- |
| `KsetCloudProperties` 及嵌套配置 | `kset.cloud` 下的网关、灰度、Nacos/Sentinel/Dubbo 治理配置由 `kset-cloud` 统一定义；对应 starter 使用这些共享字段，并不各复制一套配置类。 | [源码](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetCloudProperties.java) |
| `KsetAuthProperties` / `AuthRequest` / `AuthResult` | 前者描述规则、会话、Gateway/Web/Dubbo 配置；`AuthRequest` 承载一次认证的路径、请求头与来源；`AuthResult` 表示放行、已认证或失败。`LoginContext` 属于当前执行上下文，不是持久化会话记录。 | [配置](../../kset-starter-auth/src/main/java/com/kset/auth/config/KsetAuthProperties.java) · [请求](../../kset-starter-auth/src/main/java/com/kset/auth/core/AuthRequest.java) · [结果](../../kset-starter-auth/src/main/java/com/kset/auth/core/AuthResult.java) |
| `KsetCacheSpec` / `KsetCacheProperties` / `KsetRedisProperties` | `KsetCacheSpec` 定义一次缓存键、层级、类型与 TTL；Cache 配置控制 L1/L2 策略，Redis 配置控制连接、锁和 Redis L2 适配。缓存值是可失效副本，不是业务权威数据。 | [缓存规格](../../kset-starter-cache/src/main/java/com/kset/cache/core/KsetCacheSpec.java) · [Cache 配置](../../kset-starter-cache/src/main/java/com/kset/cache/config/KsetCacheProperties.java) · [Redis 配置](../../kset-starter-redis/src/main/java/com/kset/redis/config/KsetRedisProperties.java) |
| `AgentRequest` / `AgentResult` / `AgentRunSnapshot` | 请求身份 `agentRunId` 在同一可恢复 run 内保持稳定；结果里的快照是返回时状态投影，不能当作宿主已持久化的证明。模型/工具/协议 metadata 各有作用域。字段级约束见原有 Agent 对象契约。 | [Agent 对象契约](../../kset-agent-core/docs/core-object-contract.md) |
| `KsetScheduleProperties` / SQL 锁行 | 配置项控制锁表名与租期；锁行以 `name` 为主键，`fencing_token` 在抢占过期锁时递增。切面抢不到锁会跳过方法并返回 `null`。 | [配置](../../kset-starter-schedule/src/main/java/com/kset/schedule/config/KsetScheduleProperties.java) · [SQL 实现](../../kset-starter-schedule/src/main/java/com/kset/schedule/lock/JdbcTaskLockProvider.java) |

### 关键字段判读

| 对象 | 字段 | 含义与实际约束 |
| --- | --- | --- |
| `AuthRequest` | `path`、`source`、`method`、`params`、`headerReader` | 分别是待匹配路径、Web/Gateway 等调用来源、HTTP 方法、请求参数和按需读取请求头的函数；`params` 在构造时复制。 |
| `AuthResult` | `authenticated`、`permitAll`、`user`、`code`、`message` | `permitAll` 与已认证是不同结果；失败通过 `code/message` 表示，用户对象只在已认证结果中存在。 |
| `KsetAuthProperties` | `defaultSubject`、`defaultScheme`、`defaultTokenHeader`、`rules` | 默认主体为 `app`、方案为 `session`；规则列表按路径覆盖默认选择，实际 token 头由 `AuthHeaders.SESSION_TOKEN` 常量提供。 |
| `KsetCacheSpec` | `cacheName`、`key`、`layers`、`ttl`、`nullTtl`、`cacheNull`、`valueType` | 名称与 key 必须非空；层级列表不能为空；完整键由 `cacheName + "::" + key` 派生；null 值优先用 `nullTtl`，未指定则用 `ttl`。 |
| `KsetCacheProperties` | `defaultLayers`、`cacheNull`、`singleFlightEnabled`、`ttlJitterEnabled` | 默认仅 L1、允许缓存 null、启用同 key 本地合并；TTL 抖动默认关闭。各字段的初始表达式与嵌套 L1/L2 默认值见下表。 |
| `KsetScheduleProperties.Lock` | `tableName`、`atMostFor`、`autoCreateTable` | 默认表名 `t_kset_schedule_lock`、最长持锁 10 分钟、启动时自动建表；任务注解可覆盖租期。 |
| `ApiResponse<T>` | `code`、`message`、`data`、`traceId` | Servlet 统一响应体；`success(data)` 产生 `code=0`、`message=success`，`fail(message)` 默认 `code=-1`；traceId 可由响应 advice 补入。 |
| `AgentRequest` | `agentRunId`、`task`、`protocolId`、`options`、`attributes`、`cancellation` | `task` 必填，`protocolId` 必填；省略 options 使用默认值；请求属性递归复制，保留字段作用域；新的 run 可由内核生成 ID，resume 必须与快照一致。 |
| `AgentRunSnapshot` | `runStatus`、`turn`、`observations`、`attributes`、`stopDecision` | 快照是恢复输入和状态投影；`attributes` 含内核与策略持久化状态，不是 `AgentRequest.attributes`；恢复还会核对待处理动作身份。 |

### 动态响应对象

`kset-starter-gateway` 的错误响应由 `GatewayErrorResponse.body` 动态构造 `Map`，因此不出现在 Java 字段对象表。实际 JSON 字段为 `code: int`（HTTP 500 时为 -1，其余用 HTTP 状态值）、`message: String`（按状态映射的文案）、`data: null`、`traceId: String`（当前请求追踪 ID）。依据：[GatewayErrorResponse](../../kset-starter-gateway/src/main/java/com/kset/gateway/handler/GatewayErrorResponse.java)。Servlet 侧的 `ApiResponse<T>` 是另一对象，见下方 Web 模块。

### SQL 锁表字段

`kset-starter-schedule` 在启动时创建 `t_kset_schedule_lock`，代码内实际列定义如下；表名可由锁提供者构造参数覆盖，默认值为该名称。依据：[JdbcTaskLockProvider](../../kset-starter-schedule/src/main/java/com/kset/schedule/lock/JdbcTaskLockProvider.java)。

| 列 | SQL 类型 | 作用 |
| --- | --- | --- |
| `name` | `VARCHAR(128) PRIMARY KEY` | 锁业务名、同名任务互斥键。 |
| `lock_until` | `TIMESTAMP NOT NULL` | 当前持有期限；过期条件写的比较列。 |
| `locked_at` | `TIMESTAMP NOT NULL` | 本次抢锁时间。 |
| `locked_by` | `VARCHAR(255) NOT NULL` | 实例标识；释放锁时匹配。 |
| `fencing_token` | `BIGINT NOT NULL DEFAULT 0` | 新增锁从 1 开始，过期抢占时递增。 |

## 逐字段索引

## kset-agent-core

### `AgentActionContext`

类型：record；[源码](../../kset-agent-core/src/main/java/com/kset/agent/core/action/AgentActionContext.java)。

职责：Immutable Agent request, run state and execution context supplied to one action handler.

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`request`](../../kset-agent-core/src/main/java/com/kset/agent/core/action/AgentActionContext.java) | `AgentRequest` | — | — |
| [`state`](../../kset-agent-core/src/main/java/com/kset/agent/core/action/AgentActionContext.java) | `AgentRunState` | — | — |
| [`executionContext`](../../kset-agent-core/src/main/java/com/kset/agent/core/action/AgentActionContext.java) | `AgentExecutionContext` | — | — |
| [`clock`](../../kset-agent-core/src/main/java/com/kset/agent/core/action/AgentActionContext.java) | `Clock` | — | — |

### `AgentActionResult`

类型：record；[源码](../../kset-agent-core/src/main/java/com/kset/agent/core/action/AgentActionResult.java)。

职责：Outcome of one dispatched action. State removals are applied before state writes. A requested run status may only complete or suspend the current invocation; null means continue. answer is only a completed final answer; suspensionMessage is only a prompt for a suspended run. Technical failures must be thrown so the kernel can expose an AgentFailure.

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`observations`](../../kset-agent-core/src/main/java/com/kset/agent/core/action/AgentActionResult.java) | `List<AgentObservation>` | — | — |
| [`requestedRunStatus`](../../kset-agent-core/src/main/java/com/kset/agent/core/action/AgentActionResult.java) | `AgentRunStatus` | — | — |
| [`answer`](../../kset-agent-core/src/main/java/com/kset/agent/core/action/AgentActionResult.java) | `String` | — | — |
| [`suspensionMessage`](../../kset-agent-core/src/main/java/com/kset/agent/core/action/AgentActionResult.java) | `String` | — | — |
| [`stateAttributes`](../../kset-agent-core/src/main/java/com/kset/agent/core/action/AgentActionResult.java) | `Map<String, Object>` | — | — |
| [`removedStateAttributes`](../../kset-agent-core/src/main/java/com/kset/agent/core/action/AgentActionResult.java) | `Set<String>` | — | — |

### `AgentObservation`

类型：record；[源码](../../kset-agent-core/src/main/java/com/kset/agent/core/action/AgentObservation.java)。

职责：Structured result fed back to the next reasoning turn. progress contributes to AgentActionResult#madeProgress() and the consecutive no-progress count; it does not certify an external side effect.

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`actionType`](../../kset-agent-core/src/main/java/com/kset/agent/core/action/AgentObservation.java) | `String` | — | — |
| [`success`](../../kset-agent-core/src/main/java/com/kset/agent/core/action/AgentObservation.java) | `boolean` | — | — |
| [`progress`](../../kset-agent-core/src/main/java/com/kset/agent/core/action/AgentObservation.java) | `boolean` | — | — |
| [`output`](../../kset-agent-core/src/main/java/com/kset/agent/core/action/AgentObservation.java) | `Object` | — | — |
| [`errorCode`](../../kset-agent-core/src/main/java/com/kset/agent/core/action/AgentObservation.java) | `String` | — | — |
| [`errorMessage`](../../kset-agent-core/src/main/java/com/kset/agent/core/action/AgentObservation.java) | `String` | — | — |
| [`attributes`](../../kset-agent-core/src/main/java/com/kset/agent/core/action/AgentObservation.java) | `Map<String, Object>` | — | — |

### `AnswerChunkAction`

类型：record；[源码](../../kset-agent-core/src/main/java/com/kset/agent/core/action/AnswerChunkAction.java)。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`text`](../../kset-agent-core/src/main/java/com/kset/agent/core/action/AnswerChunkAction.java) | `String` | — | — |

### `ConfirmationAction`

类型：record；[源码](../../kset-agent-core/src/main/java/com/kset/agent/core/action/ConfirmationAction.java)。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`confirmationId`](../../kset-agent-core/src/main/java/com/kset/agent/core/action/ConfirmationAction.java) | `String` | — | — |
| [`message`](../../kset-agent-core/src/main/java/com/kset/agent/core/action/ConfirmationAction.java) | `String` | — | — |
| [`options`](../../kset-agent-core/src/main/java/com/kset/agent/core/action/ConfirmationAction.java) | `Map<String, Object>` | — | — |

### `ExtensionAction`

类型：record；[源码](../../kset-agent-core/src/main/java/com/kset/agent/core/action/ExtensionAction.java)。

职责：Action carrier for protocol extensions that introduce a custom action type.

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`actionType`](../../kset-agent-core/src/main/java/com/kset/agent/core/action/ExtensionAction.java) | `String` | — | — |
| [`payload`](../../kset-agent-core/src/main/java/com/kset/agent/core/action/ExtensionAction.java) | `Map<String, Object>` | — | — |

### `FinalAnswerAction`

类型：record；[源码](../../kset-agent-core/src/main/java/com/kset/agent/core/action/FinalAnswerAction.java)。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`answer`](../../kset-agent-core/src/main/java/com/kset/agent/core/action/FinalAnswerAction.java) | `String` | — | — |

### `PlanTask`

类型：record；[源码](../../kset-agent-core/src/main/java/com/kset/agent/core/action/PlanTask.java)。

职责：One model-created plan task identified by taskId and linked by dependsOn task IDs.

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`taskId`](../../kset-agent-core/src/main/java/com/kset/agent/core/action/PlanTask.java) | `String` | — | — |
| [`title`](../../kset-agent-core/src/main/java/com/kset/agent/core/action/PlanTask.java) | `String` | — | — |
| [`dependsOn`](../../kset-agent-core/src/main/java/com/kset/agent/core/action/PlanTask.java) | `List<String>` | — | — |

### `TaskPlanAction`

类型：record；[源码](../../kset-agent-core/src/main/java/com/kset/agent/core/action/TaskPlanAction.java)。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`summary`](../../kset-agent-core/src/main/java/com/kset/agent/core/action/TaskPlanAction.java) | `String` | — | — |
| [`tasks`](../../kset-agent-core/src/main/java/com/kset/agent/core/action/TaskPlanAction.java) | `List<PlanTask>` | — | — |

### `ToolBatchAction`

类型：record；[源码](../../kset-agent-core/src/main/java/com/kset/agent/core/action/ToolBatchAction.java)。

职责：A protocol-neutral batch of independent tool calls.

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`summary`](../../kset-agent-core/src/main/java/com/kset/agent/core/action/ToolBatchAction.java) | `String` | — | — |
| [`toolCalls`](../../kset-agent-core/src/main/java/com/kset/agent/core/action/ToolBatchAction.java) | `List<ToolCallAction>` | — | — |

### `ToolCallAction`

类型：record；[源码](../../kset-agent-core/src/main/java/com/kset/agent/core/action/ToolCallAction.java)。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`callId`](../../kset-agent-core/src/main/java/com/kset/agent/core/action/ToolCallAction.java) | `String` | — | — |
| [`taskId`](../../kset-agent-core/src/main/java/com/kset/agent/core/action/ToolCallAction.java) | `String` | — | — |
| [`toolName`](../../kset-agent-core/src/main/java/com/kset/agent/core/action/ToolCallAction.java) | `String` | — | — |
| [`arguments`](../../kset-agent-core/src/main/java/com/kset/agent/core/action/ToolCallAction.java) | `Map<String, Object>` | — | — |

### `AgentFailure`

类型：record；[源码](../../kset-agent-core/src/main/java/com/kset/agent/core/api/AgentFailure.java)。

职责：Stable technical error code and safe message returned for an active-loop failure.

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`errorCode`](../../kset-agent-core/src/main/java/com/kset/agent/core/api/AgentFailure.java) | `AgentErrorCode` | — | — |
| [`errorMessage`](../../kset-agent-core/src/main/java/com/kset/agent/core/api/AgentFailure.java) | `String` | — | — |
| [`retryable`](../../kset-agent-core/src/main/java/com/kset/agent/core/api/AgentFailure.java) | `boolean` | — | — |

### `AgentRequest`

类型：record；[源码](../../kset-agent-core/src/main/java/com/kset/agent/core/api/AgentRequest.java)。

职责：Input to a new or resumed Agent run. A new request may omit agentRunId when the kernel has an ID generator; resume requests must provide the snapshot agentRunId. Structured attribute containers are recursively snapshotted; opaque values must be immutable or scoped to this request.

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`agentRunId`](../../kset-agent-core/src/main/java/com/kset/agent/core/api/AgentRequest.java) | `String` | — | — |
| [`task`](../../kset-agent-core/src/main/java/com/kset/agent/core/api/AgentRequest.java) | `String` | — | — |
| [`protocolId`](../../kset-agent-core/src/main/java/com/kset/agent/core/api/AgentRequest.java) | `AgentProtocolId` | — | — |
| [`options`](../../kset-agent-core/src/main/java/com/kset/agent/core/api/AgentRequest.java) | `AgentLoopOptions` | — | — |
| [`attributes`](../../kset-agent-core/src/main/java/com/kset/agent/core/api/AgentRequest.java) | `Map<String, Object>` | — | — |
| [`cancellation`](../../kset-agent-core/src/main/java/com/kset/agent/core/api/AgentRequest.java) | `AgentCancellation` | — | — |

### `AgentResult`

类型：record；[源码](../../kset-agent-core/src/main/java/com/kset/agent/core/api/AgentResult.java)。

职责：Immutable result of one synchronous run or resume invocation. The run status and stop decision always describe the same terminal or suspended state. answer is non-null only for a completed run; a wait prompt is in stopDecision.stopMessage, never in answer.

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`agentRunId`](../../kset-agent-core/src/main/java/com/kset/agent/core/api/AgentResult.java) | `String` | — | — |
| [`runStatus`](../../kset-agent-core/src/main/java/com/kset/agent/core/api/AgentResult.java) | `AgentRunStatus` | — | — |
| [`answer`](../../kset-agent-core/src/main/java/com/kset/agent/core/api/AgentResult.java) | `String` | — | — |
| [`stopDecision`](../../kset-agent-core/src/main/java/com/kset/agent/core/api/AgentResult.java) | `AgentStopDecision` | — | — |
| [`snapshot`](../../kset-agent-core/src/main/java/com/kset/agent/core/api/AgentResult.java) | `AgentRunSnapshot` | — | — |
| [`failure`](../../kset-agent-core/src/main/java/com/kset/agent/core/api/AgentResult.java) | `AgentFailure` | — | — |

### `AgentResumeInput`

类型：record；[源码](../../kset-agent-core/src/main/java/com/kset/agent/core/api/AgentResumeInput.java)。

职责：Authoritative external results used to resume a pending reconciliation. Build entries with AgentObservation#toolResult(String, String, String, com.kset.agent.core.tool.ToolExecutionResult).

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`reconciledObservations`](../../kset-agent-core/src/main/java/com/kset/agent/core/api/AgentResumeInput.java) | `List<AgentObservation>` | — | — |

### `AgentLifecycleContext`

类型：record；[源码](../../kset-agent-core/src/main/java/com/kset/agent/core/event/AgentLifecycleContext.java)。

职责：Stable Agent identity, eventSequence and timing envelope for every lifecycle callback.

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`version`](../../kset-agent-core/src/main/java/com/kset/agent/core/event/AgentLifecycleContext.java) | `int` | — | — |
| [`eventType`](../../kset-agent-core/src/main/java/com/kset/agent/core/event/AgentLifecycleContext.java) | `AgentLifecycleEventType` | — | — |
| [`invocationType`](../../kset-agent-core/src/main/java/com/kset/agent/core/event/AgentLifecycleContext.java) | `AgentInvocationType` | — | — |
| [`invocationId`](../../kset-agent-core/src/main/java/com/kset/agent/core/event/AgentLifecycleContext.java) | `String` | — | — |
| [`eventSequence`](../../kset-agent-core/src/main/java/com/kset/agent/core/event/AgentLifecycleContext.java) | `long` | — | — |
| [`agentRunId`](../../kset-agent-core/src/main/java/com/kset/agent/core/event/AgentLifecycleContext.java) | `String` | — | — |
| [`stepId`](../../kset-agent-core/src/main/java/com/kset/agent/core/event/AgentLifecycleContext.java) | `String` | — | — |
| [`parentStepId`](../../kset-agent-core/src/main/java/com/kset/agent/core/event/AgentLifecycleContext.java) | `String` | — | — |
| [`stepType`](../../kset-agent-core/src/main/java/com/kset/agent/core/event/AgentLifecycleContext.java) | `AgentLifecycleStepType` | — | — |
| [`operation`](../../kset-agent-core/src/main/java/com/kset/agent/core/event/AgentLifecycleContext.java) | `String` | — | — |
| [`turn`](../../kset-agent-core/src/main/java/com/kset/agent/core/event/AgentLifecycleContext.java) | `int` | — | — |
| [`actionIndex`](../../kset-agent-core/src/main/java/com/kset/agent/core/event/AgentLifecycleContext.java) | `int` | — | — |
| [`occurredAt`](../../kset-agent-core/src/main/java/com/kset/agent/core/event/AgentLifecycleContext.java) | `Instant` | — | — |
| [`executionStartedAt`](../../kset-agent-core/src/main/java/com/kset/agent/core/event/AgentLifecycleContext.java) | `Instant` | — | — |
| [`deadline`](../../kset-agent-core/src/main/java/com/kset/agent/core/event/AgentLifecycleContext.java) | `Instant` | — | — |
| [`elapsed`](../../kset-agent-core/src/main/java/com/kset/agent/core/event/AgentLifecycleContext.java) | `Duration` | — | — |

### `AgentExecutionContext`

类型：record；[源码](../../kset-agent-core/src/main/java/com/kset/agent/core/execution/AgentExecutionContext.java)。

职责：Immutable trace, cancellation and timing context for one active kernel operation.

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`agentRunId`](../../kset-agent-core/src/main/java/com/kset/agent/core/execution/AgentExecutionContext.java) | `String` | — | — |
| [`invocationId`](../../kset-agent-core/src/main/java/com/kset/agent/core/execution/AgentExecutionContext.java) | `String` | — | — |
| [`stepId`](../../kset-agent-core/src/main/java/com/kset/agent/core/execution/AgentExecutionContext.java) | `String` | — | — |
| [`parentStepId`](../../kset-agent-core/src/main/java/com/kset/agent/core/execution/AgentExecutionContext.java) | `String` | — | — |
| [`stepType`](../../kset-agent-core/src/main/java/com/kset/agent/core/execution/AgentExecutionContext.java) | `AgentLifecycleStepType` | — | — |
| [`operation`](../../kset-agent-core/src/main/java/com/kset/agent/core/execution/AgentExecutionContext.java) | `String` | — | — |
| [`turn`](../../kset-agent-core/src/main/java/com/kset/agent/core/execution/AgentExecutionContext.java) | `int` | — | — |
| [`executionStartedAt`](../../kset-agent-core/src/main/java/com/kset/agent/core/execution/AgentExecutionContext.java) | `Instant` | — | — |
| [`issuedAt`](../../kset-agent-core/src/main/java/com/kset/agent/core/execution/AgentExecutionContext.java) | `Instant` | — | — |
| [`deadline`](../../kset-agent-core/src/main/java/com/kset/agent/core/execution/AgentExecutionContext.java) | `Instant` | — | — |
| [`cancellation`](../../kset-agent-core/src/main/java/com/kset/agent/core/execution/AgentExecutionContext.java) | `AgentCancellation` | — | — |
| [`attributes`](../../kset-agent-core/src/main/java/com/kset/agent/core/execution/AgentExecutionContext.java) | `Map<String, Object>` | — | — |

### `AgentLoopOptions`

类型：record；[源码](../../kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentLoopOptions.java)。

职责：Immutable run limits and per-invocation execution deadlines. maxPromptChars counts UTF-16 code units in the prepared system and user prompts only. maxObservationSummaryChars bounds one default ReAct model-visible summary, not the original observation retained in the snapshot.

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`maxTurns`](../../kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentLoopOptions.java) | `int` | — | — |
| [`invocationTimeout`](../../kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentLoopOptions.java) | `Duration` | — | — |
| [`protocolErrorLimit`](../../kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentLoopOptions.java) | `int` | — | — |
| [`noProgressLimit`](../../kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentLoopOptions.java) | `int` | — | — |
| [`maxOutputTokens`](../../kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentLoopOptions.java) | `int` | — | — |
| [`toolCallTimeout`](../../kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentLoopOptions.java) | `Duration` | — | — |
| [`toolBatchTimeout`](../../kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentLoopOptions.java) | `Duration` | — | — |
| [`maxActionsPerDecision`](../../kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentLoopOptions.java) | `int` | — | — |
| [`maxToolCallsPerBatch`](../../kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentLoopOptions.java) | `int` | — | — |
| [`maxPromptChars`](../../kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentLoopOptions.java) | `int` | — | — |
| [`maxObservationSummaryChars`](../../kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentLoopOptions.java) | `int` | — | — |

### `AgentRunSnapshot`

类型：record；[源码](../../kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentRunSnapshot.java)。

职责：Serializable run state. Version 4 preserves approval batches and reconciliation state.

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`version`](../../kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentRunSnapshot.java) | `int` | — | — |
| [`agentRunId`](../../kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentRunSnapshot.java) | `String` | — | — |
| [`task`](../../kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentRunSnapshot.java) | `String` | — | — |
| [`protocolId`](../../kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentRunSnapshot.java) | `AgentProtocolId` | — | — |
| [`runStatus`](../../kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentRunSnapshot.java) | `AgentRunStatus` | — | — |
| [`turn`](../../kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentRunSnapshot.java) | `int` | — | — |
| [`startedAt`](../../kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentRunSnapshot.java) | `Instant` | — | — |
| [`updatedAt`](../../kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentRunSnapshot.java) | `Instant` | — | — |
| [`observations`](../../kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentRunSnapshot.java) | `List<AgentObservation>` | — | — |
| [`attributes`](../../kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentRunSnapshot.java) | `Map<String, Object>` | — | — |
| [`consecutiveProtocolErrors`](../../kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentRunSnapshot.java) | `int` | — | — |
| [`consecutiveNoProgress`](../../kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentRunSnapshot.java) | `int` | — | — |
| [`answer`](../../kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentRunSnapshot.java) | `String` | — | — |
| [`suspensionMessage`](../../kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentRunSnapshot.java) | `String` | — | — |
| [`stopDecision`](../../kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentRunSnapshot.java) | `AgentStopDecision` | — | — |

### `AgentRunState`

类型：class；[源码](../../kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentRunState.java)。

职责：Immutable runtime state owned by the loop kernel; answer and wait text are separate.

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`agentRunId`](../../kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentRunState.java) | `String` | — | — |
| [`task`](../../kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentRunState.java) | `String` | — | — |
| [`protocolId`](../../kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentRunState.java) | `AgentProtocolId` | — | — |
| [`runStatus`](../../kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentRunState.java) | `AgentRunStatus` | — | — |
| [`turn`](../../kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentRunState.java) | `int` | — | — |
| [`startedAt`](../../kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentRunState.java) | `Instant` | — | — |
| [`updatedAt`](../../kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentRunState.java) | `Instant` | — | — |
| [`observations`](../../kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentRunState.java) | `List<AgentObservation>` | — | — |
| [`attributes`](../../kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentRunState.java) | `Map<String, Object>` | — | — |
| [`consecutiveProtocolErrors`](../../kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentRunState.java) | `int` | — | — |
| [`consecutiveNoProgress`](../../kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentRunState.java) | `int` | — | — |
| [`answer`](../../kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentRunState.java) | `String` | — | — |
| [`suspensionMessage`](../../kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentRunState.java) | `String` | — | — |
| [`stopDecision`](../../kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentRunState.java) | `AgentStopDecision` | — | — |

### `AgentModelRetryOptions`

类型：record；[源码](../../kset-agent-core/src/main/java/com/kset/agent/core/model/AgentModelRetryOptions.java)。

职责：Immutable limits for retryable model-call failures within one active invocation.

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`maxAttempts`](../../kset-agent-core/src/main/java/com/kset/agent/core/model/AgentModelRetryOptions.java) | `int` | — | — |
| [`initialDelay`](../../kset-agent-core/src/main/java/com/kset/agent/core/model/AgentModelRetryOptions.java) | `Duration` | — | — |
| [`maxDelay`](../../kset-agent-core/src/main/java/com/kset/agent/core/model/AgentModelRetryOptions.java) | `Duration` | — | — |

### `ModelCallMetrics`

类型：record；[源码](../../kset-agent-core/src/main/java/com/kset/agent/core/model/ModelCallMetrics.java)。

职责：Provider-neutral model identity and usage values intended for external telemetry.

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`provider`](../../kset-agent-core/src/main/java/com/kset/agent/core/model/ModelCallMetrics.java) | `String` | — | — |
| [`model`](../../kset-agent-core/src/main/java/com/kset/agent/core/model/ModelCallMetrics.java) | `String` | — | — |
| [`requestId`](../../kset-agent-core/src/main/java/com/kset/agent/core/model/ModelCallMetrics.java) | `String` | — | — |
| [`inputTokens`](../../kset-agent-core/src/main/java/com/kset/agent/core/model/ModelCallMetrics.java) | `Long` | — | — |
| [`outputTokens`](../../kset-agent-core/src/main/java/com/kset/agent/core/model/ModelCallMetrics.java) | `Long` | — | — |
| [`totalTokens`](../../kset-agent-core/src/main/java/com/kset/agent/core/model/ModelCallMetrics.java) | `Long` | — | — |
| [`attributes`](../../kset-agent-core/src/main/java/com/kset/agent/core/model/ModelCallMetrics.java) | `Map<String, Object>` | — | — |

### `ModelRequest`

类型：record；[源码](../../kset-agent-core/src/main/java/com/kset/agent/core/model/ModelRequest.java)。

职责：Provider-neutral model request. The strategy supplies attributes as context for the model adapter to interpret; the adapter must select provider fields explicitly instead of forwarding the entire map. Prompt character limits do not replace provider token-window checks.

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`systemPrompt`](../../kset-agent-core/src/main/java/com/kset/agent/core/model/ModelRequest.java) | `String` | — | — |
| [`userPrompt`](../../kset-agent-core/src/main/java/com/kset/agent/core/model/ModelRequest.java) | `String` | — | — |
| [`maxOutputTokens`](../../kset-agent-core/src/main/java/com/kset/agent/core/model/ModelRequest.java) | `int` | — | — |
| [`attributes`](../../kset-agent-core/src/main/java/com/kset/agent/core/model/ModelRequest.java) | `Map<String, Object>` | — | — |

### `ModelResponse`

类型：record；[源码](../../kset-agent-core/src/main/java/com/kset/agent/core/model/ModelResponse.java)。

职责：Provider-neutral model response, including optional native tool calls.

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`text`](../../kset-agent-core/src/main/java/com/kset/agent/core/model/ModelResponse.java) | `String` | — | — |
| [`toolCalls`](../../kset-agent-core/src/main/java/com/kset/agent/core/model/ModelResponse.java) | `List<ModelToolCall>` | — | — |
| [`finishReason`](../../kset-agent-core/src/main/java/com/kset/agent/core/model/ModelResponse.java) | `String` | — | — |
| [`metrics`](../../kset-agent-core/src/main/java/com/kset/agent/core/model/ModelResponse.java) | `ModelCallMetrics` | — | — |
| [`metadata`](../../kset-agent-core/src/main/java/com/kset/agent/core/model/ModelResponse.java) | `Map<String, Object>` | — | — |

### `ModelToolCall`

类型：record；[源码](../../kset-agent-core/src/main/java/com/kset/agent/core/model/ModelToolCall.java)。

职责：Provider-native tool call normalized to the core callId/toolName vocabulary.

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`callId`](../../kset-agent-core/src/main/java/com/kset/agent/core/model/ModelToolCall.java) | `String` | — | — |
| [`toolName`](../../kset-agent-core/src/main/java/com/kset/agent/core/model/ModelToolCall.java) | `String` | — | — |
| [`arguments`](../../kset-agent-core/src/main/java/com/kset/agent/core/model/ModelToolCall.java) | `Map<String, Object>` | — | — |

### `AgentDecision`

类型：record；[源码](../../kset-agent-core/src/main/java/com/kset/agent/core/protocol/AgentDecision.java)。

职责：Protocol-neutral decision consumed by the loop kernel.

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`actions`](../../kset-agent-core/src/main/java/com/kset/agent/core/protocol/AgentDecision.java) | `List<AgentAction>` | — | — |
| [`metadata`](../../kset-agent-core/src/main/java/com/kset/agent/core/protocol/AgentDecision.java) | `Map<String, Object>` | — | — |

### `AgentProtocolContext`

类型：record；[源码](../../kset-agent-core/src/main/java/com/kset/agent/core/protocol/AgentProtocolContext.java)。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`request`](../../kset-agent-core/src/main/java/com/kset/agent/core/protocol/AgentProtocolContext.java) | `AgentRequest` | — | — |
| [`state`](../../kset-agent-core/src/main/java/com/kset/agent/core/protocol/AgentProtocolContext.java) | `AgentRunState` | — | — |

### `AgentProtocolId`

类型：record；[源码](../../kset-agent-core/src/main/java/com/kset/agent/core/protocol/AgentProtocolId.java)。

职责：Stable protocolName/protocolVersion identity used for lookup and snapshot compatibility.

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`protocolName`](../../kset-agent-core/src/main/java/com/kset/agent/core/protocol/AgentProtocolId.java) | `String` | — | — |
| [`protocolVersion`](../../kset-agent-core/src/main/java/com/kset/agent/core/protocol/AgentProtocolId.java) | `String` | — | — |

### `AgentStopContext`

类型：record；[源码](../../kset-agent-core/src/main/java/com/kset/agent/core/stop/AgentStopContext.java)。

职责：Read-only Agent state and execution context supplied to one stop-policy evaluation.

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`request`](../../kset-agent-core/src/main/java/com/kset/agent/core/stop/AgentStopContext.java) | `AgentRequest` | — | — |
| [`state`](../../kset-agent-core/src/main/java/com/kset/agent/core/stop/AgentStopContext.java) | `AgentRunState` | — | — |
| [`executionContext`](../../kset-agent-core/src/main/java/com/kset/agent/core/stop/AgentStopContext.java) | `AgentExecutionContext` | — | — |
| [`now`](../../kset-agent-core/src/main/java/com/kset/agent/core/stop/AgentStopContext.java) | `Instant` | — | — |

### `AgentStopDecision`

类型：record；[源码](../../kset-agent-core/src/main/java/com/kset/agent/core/stop/AgentStopDecision.java)。

职责：Authoritative stop decision; stopMessage is readable text, not a branching code.

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`shouldStop`](../../kset-agent-core/src/main/java/com/kset/agent/core/stop/AgentStopDecision.java) | `boolean` | — | — |
| [`stopReason`](../../kset-agent-core/src/main/java/com/kset/agent/core/stop/AgentStopDecision.java) | `AgentStopReason` | — | — |
| [`runStatus`](../../kset-agent-core/src/main/java/com/kset/agent/core/stop/AgentStopDecision.java) | `AgentRunStatus` | — | — |
| [`stopMessage`](../../kset-agent-core/src/main/java/com/kset/agent/core/stop/AgentStopDecision.java) | `String` | — | — |

### `AgentTurn`

类型：record；[源码](../../kset-agent-core/src/main/java/com/kset/agent/core/strategy/AgentTurn.java)。

职责：Model request and protocol selected for one reasoning turn.

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`protocolId`](../../kset-agent-core/src/main/java/com/kset/agent/core/strategy/AgentTurn.java) | `AgentProtocolId` | — | — |
| [`modelRequest`](../../kset-agent-core/src/main/java/com/kset/agent/core/strategy/AgentTurn.java) | `ModelRequest` | — | — |

### `AgentToolContext`

类型：record；[源码](../../kset-agent-core/src/main/java/com/kset/agent/core/tool/AgentToolContext.java)。

职责：Tool identity plus the immutable invocation and action-step execution context.

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`executionContext`](../../kset-agent-core/src/main/java/com/kset/agent/core/tool/AgentToolContext.java) | `AgentExecutionContext` | — | — |
| [`callId`](../../kset-agent-core/src/main/java/com/kset/agent/core/tool/AgentToolContext.java) | `String` | — | — |
| [`taskId`](../../kset-agent-core/src/main/java/com/kset/agent/core/tool/AgentToolContext.java) | `String` | — | — |
| [`toolName`](../../kset-agent-core/src/main/java/com/kset/agent/core/tool/AgentToolContext.java) | `String` | — | — |
| [`deadline`](../../kset-agent-core/src/main/java/com/kset/agent/core/tool/AgentToolContext.java) | `Instant` | — | — |

### `AgentToolDescriptor`

类型：record；[源码](../../kset-agent-core/src/main/java/com/kset/agent/core/tool/AgentToolDescriptor.java)。

职责：Provider-neutral tool description exposed to reasoning strategies.

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`toolName`](../../kset-agent-core/src/main/java/com/kset/agent/core/tool/AgentToolDescriptor.java) | `String` | — | — |
| [`description`](../../kset-agent-core/src/main/java/com/kset/agent/core/tool/AgentToolDescriptor.java) | `String` | — | — |
| [`inputSchema`](../../kset-agent-core/src/main/java/com/kset/agent/core/tool/AgentToolDescriptor.java) | `Map<String, Object>` | — | — |
| [`readOnly`](../../kset-agent-core/src/main/java/com/kset/agent/core/tool/AgentToolDescriptor.java) | `boolean` | — | — |
| [`requiresConfirmation`](../../kset-agent-core/src/main/java/com/kset/agent/core/tool/AgentToolDescriptor.java) | `boolean` | — | — |
| [`metadata`](../../kset-agent-core/src/main/java/com/kset/agent/core/tool/AgentToolDescriptor.java) | `Map<String, Object>` | — | — |

### `AgentToolIdempotencyKey`

类型：record；[源码](../../kset-agent-core/src/main/java/com/kset/agent/core/tool/AgentToolIdempotencyKey.java)。

职责：Structural idempotency key for one tool operation within an Agent run.

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`agentRunId`](../../kset-agent-core/src/main/java/com/kset/agent/core/tool/AgentToolIdempotencyKey.java) | `String` | — | — |
| [`callId`](../../kset-agent-core/src/main/java/com/kset/agent/core/tool/AgentToolIdempotencyKey.java) | `String` | — | — |

### `ToolExecutionResult`

类型：record；[源码](../../kset-agent-core/src/main/java/com/kset/agent/core/tool/ToolExecutionResult.java)。

职责：Structured result returned by an Agent tool. Use AgentErrorCode#TOOL_RESULT_UNKNOWN when a side effect may have occurred but no authoritative result is available. progress reports whether a successful result resets the Agent loop's no-progress counter; it does not certify that an external side effect was committed. #success(Object) reports progress, while #unchanged(Object) does not.

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`success`](../../kset-agent-core/src/main/java/com/kset/agent/core/tool/ToolExecutionResult.java) | `boolean` | — | — |
| [`progress`](../../kset-agent-core/src/main/java/com/kset/agent/core/tool/ToolExecutionResult.java) | `boolean` | — | — |
| [`output`](../../kset-agent-core/src/main/java/com/kset/agent/core/tool/ToolExecutionResult.java) | `Object` | — | — |
| [`errorCode`](../../kset-agent-core/src/main/java/com/kset/agent/core/tool/ToolExecutionResult.java) | `String` | — | — |
| [`errorMessage`](../../kset-agent-core/src/main/java/com/kset/agent/core/tool/ToolExecutionResult.java) | `String` | — | — |
| [`attributes`](../../kset-agent-core/src/main/java/com/kset/agent/core/tool/ToolExecutionResult.java) | `Map<String, Object>` | — | — |

## kset-cloud

### `KsetCloudProperties`

类型：class；配置前缀：`kset.cloud`；[源码](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetCloudProperties.java)。

职责：KSet 云服务统一配置

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`nacos`](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetCloudProperties.java) | `Nacos` | `new Nacos()` | — |
| [`sentinel`](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetCloudProperties.java) | `Sentinel` | `new Sentinel()` | — |
| [`dubbo`](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetCloudProperties.java) | `Dubbo` | `new Dubbo()` | — |
| [`loadbalancer`](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetCloudProperties.java) | `Loadbalancer` | `new Loadbalancer()` | — |
| [`gateway`](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetCloudProperties.java) | `Gateway` | `new Gateway()` | — |

### `KsetCloudProperties.Nacos`

类型：class；[源码](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetCloudProperties.java)。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`namespace`](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetCloudProperties.java) | `String` | `"dev"` | 仅作缺省回退；业务请优先配置 spring.cloud.nacos.config/discovery.namespace |
| [`group`](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetCloudProperties.java) | `String` | `"KSET_GROUP"` | 仅作缺省回退；业务请优先配置 spring.cloud.nacos.config/discovery.group |
| [`configPrefix`](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetCloudProperties.java) | `String` | `"kset"` | — |
| [`commonConfigDataId`](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetCloudProperties.java) | `String` | `"kset-common.yaml"` | — |

### `KsetCloudProperties.Sentinel`

类型：class；[源码](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetCloudProperties.java)。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`enabled`](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetCloudProperties.java) | `boolean` | `true` | — |
| [`flowRuleDataId`](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetCloudProperties.java) | `String` | — | — |
| [`degradeRuleDataId`](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetCloudProperties.java) | `String` | — | — |
| [`paramFlowRuleDataId`](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetCloudProperties.java) | `String` | — | — |
| [`gatewayFlowRuleDataId`](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetCloudProperties.java) | `String` | — | — |

### `KsetCloudProperties.Dubbo`

类型：class；[源码](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetCloudProperties.java)。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`tracePropagationEnabled`](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetCloudProperties.java) | `boolean` | `true` | — |
| [`grayEnabled`](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetCloudProperties.java) | `boolean` | `true` | — |
| [`grayMetadataKey`](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetCloudProperties.java) | `String` | `"version"` | — |
| [`defaultGrayTag`](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetCloudProperties.java) | `String` | `"stable"` | — |

### `KsetCloudProperties.Loadbalancer`

类型：class；[源码](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetCloudProperties.java)。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`grayHeader`](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetCloudProperties.java) | `String` | `"X-Gray-Tag"` | — |
| [`metadataKey`](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetCloudProperties.java) | `String` | `"version"` | — |

### `KsetCloudProperties.Gateway`

类型：class；[源码](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetCloudProperties.java)。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`enabled`](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetCloudProperties.java) | `boolean` | `true` | — |
| [`routeDataId`](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetCloudProperties.java) | `String` | — | — |
| [`sentinelEnabled`](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetCloudProperties.java) | `boolean` | `true` | — |
| [`authEnabled`](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetCloudProperties.java) | `boolean` | `false` | — |
| [`corsEnabled`](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetCloudProperties.java) | `boolean` | `false` | — |
| [`authTokenHeader`](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetCloudProperties.java) | `String` | `"X-Auth-Token"` | — |
| [`authToken`](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetCloudProperties.java) | `String` | — | — |
| [`traceHeader`](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetCloudProperties.java) | `String` | `"X-Trace-Id"` | — |
| [`grayHeader`](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetCloudProperties.java) | `String` | `"X-Gray-Tag"` | — |

### `KsetDatasourceProperties`

类型：class；配置前缀：`kset.datasource`；[源码](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetDatasourceProperties.java)。

职责：数据源组件开关。 <p>MyBatis-Plus、Flyway 和 dynamic-datasource 均优先使用开源组件原生配置： mybatis-plus.*、spring.flyway.*、spring.datasource.dynamic.*。</p> @deprecated 1.0.15 起下沉至 kset-starter-datasource 的 com.kset.datasource.config.KsetDatasourceProperties， 本类仅为兼容保留，配置键 kset.datasource.* 不变；将在后续版本移除。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`enabled`](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetDatasourceProperties.java) | `boolean` | `true` | — |
| [`autoFill`](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetDatasourceProperties.java) | `boolean` | `true` | 是否注册 createTime / updateTime、createdAt / updatedAt、createDate / updateDate 自动填充处理器。 |

### `KsetRedisProperties`

类型：class；配置前缀：`kset.redis`；[源码](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetRedisProperties.java)。

职责：Redis 约定配置。 @deprecated 1.0.15 起下沉至 kset-starter-redis 的 com.kset.redis.config.KsetRedisProperties， 本类仅为兼容保留，配置键 kset.redis.* 不变；将在后续版本移除。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`enabled`](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetRedisProperties.java) | `boolean` | `true` | — |
| [`keyPrefix`](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetRedisProperties.java) | `String` | `""` | — |
| [`defaultTtl`](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetRedisProperties.java) | `Duration` | `Duration.ofMinutes(30)` | 全局默认过期时间，禁止永久 key；写操作未传 ttl 时使用 |
| [`maxTtl`](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetRedisProperties.java) | `Duration` | — | 可选：单次写入允许的最大 TTL |
| [`cache`](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetRedisProperties.java) | `Cache` | `new Cache()` | — |
| [`stream`](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetRedisProperties.java) | `Stream` | `new Stream()` | — |
| [`redisson`](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetRedisProperties.java) | `Redisson` | `new Redisson()` | — |
| [`lock`](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetRedisProperties.java) | `Lock` | `new Lock()` | — |
| [`sources`](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetRedisProperties.java) | `Map<String, RedisSourceProperties>` | `new LinkedHashMap<>()` | — |

### `KsetRedisProperties.Lock`

类型：class；[源码](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetRedisProperties.java)。

职责：分布式锁（注解锁等）。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`annotationEnabled`](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetRedisProperties.java) | `boolean` | `true` | 是否注册 @KsetLocked AOP 切面 |
| [`validateTargets`](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetRedisProperties.java) | `boolean` | `true` | 启动时校验注解锁目标并输出失效场景提示 |

### `KsetRedisProperties.Stream`

类型：class；[源码](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetRedisProperties.java)。

职责：高危操作流式/分批参数。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`scanBatchSize`](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetRedisProperties.java) | `int` | `500` | — |
| [`deleteBatchSize`](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetRedisProperties.java) | `int` | `500` | — |
| [`mgetChunkSize`](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetRedisProperties.java) | `int` | `100` | — |
| [`hashScanCount`](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetRedisProperties.java) | `int` | `100` | — |
| [`useUnlink`](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetRedisProperties.java) | `boolean` | `true` | — |

### `KsetRedisProperties.Redisson`

类型：class；[源码](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetRedisProperties.java)。

职责：Redisson 分布式锁与通用编解码（Jackson）。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`enabled`](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetRedisProperties.java) | `boolean` | `true` | — |
| [`lockWaitTime`](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetRedisProperties.java) | `Duration` | `Duration.ofSeconds(3)` | — |
| [`lockLeaseTime`](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetRedisProperties.java) | `Duration` | `Duration.ofSeconds(30)` | — |
| [`threads`](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetRedisProperties.java) | `int` | `16` | — |
| [`nettyThreads`](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetRedisProperties.java) | `int` | `32` | — |

### `KsetRedisProperties.Cache`

类型：class；[源码](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetRedisProperties.java)。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`enabled`](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetRedisProperties.java) | `boolean` | `false` | — |
| [`defaultTtl`](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetRedisProperties.java) | `Duration` | `Duration.ofHours(1)` | — |

### `KsetRedisProperties.RedisSourceProperties`

类型：class；[源码](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetRedisProperties.java)。

职责：命名 Redis 数据源（kset.redis.sources.{name}）。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`enabled`](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetRedisProperties.java) | `boolean` | `true` | — |
| [`keyPrefix`](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetRedisProperties.java) | `String` | `""` | — |
| [`host`](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetRedisProperties.java) | `String` | `"localhost"` | — |
| [`port`](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetRedisProperties.java) | `int` | `6379` | — |
| [`database`](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetRedisProperties.java) | `int` | `0` | — |
| [`password`](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetRedisProperties.java) | `String` | — | — |
| [`ssl`](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetRedisProperties.java) | `boolean` | `false` | — |
| [`timeout`](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetRedisProperties.java) | `Duration` | `Duration.ofSeconds(2)` | — |
| [`pool`](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetRedisProperties.java) | `RedisSourcePoolProperties` | `new RedisSourcePoolProperties()` | — |
| [`cluster`](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetRedisProperties.java) | `Cluster` | `new Cluster()` | — |

### `KsetRedisProperties.RedisSourcePoolProperties`

类型：class；[源码](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetRedisProperties.java)。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`enabled`](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetRedisProperties.java) | `boolean` | `false` | — |
| [`maxActive`](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetRedisProperties.java) | `int` | `8` | — |
| [`maxIdle`](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetRedisProperties.java) | `int` | `8` | — |
| [`minIdle`](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetRedisProperties.java) | `int` | `0` | — |
| [`maxWait`](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetRedisProperties.java) | `Duration` | `Duration.ofMillis(-1)` | — |

### `KsetRedisProperties.Cluster`

类型：class；[源码](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetRedisProperties.java)。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`enabled`](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetRedisProperties.java) | `boolean` | `false` | — |
| [`nodes`](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetRedisProperties.java) | `List<String>` | `List.of()` | — |
| [`maxRedirects`](../../kset-cloud/src/main/java/com/kset/cloud/config/KsetRedisProperties.java) | `int` | `3` | — |

## kset-common

### `LoginContextSnapshot`

类型：class；[源码](../../kset-common/src/main/java/com/kset/common/auth/LoginContextSnapshot.java)。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`snapshot`](../../kset-common/src/main/java/com/kset/common/auth/LoginContextSnapshot.java) | `KsetContextSnapshot` | — | — |

### `KsetContextSnapshot`

类型：class；[源码](../../kset-common/src/main/java/com/kset/common/context/KsetContextSnapshot.java)。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`values`](../../kset-common/src/main/java/com/kset/common/context/KsetContextSnapshot.java) | `Map<String, Object>` | — | — |

### `KsetEventProperties`

类型：class；配置前缀：`kset.event`；[源码](../../kset-common/src/main/java/com/kset/common/event/config/KsetEventProperties.java)。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`enabled`](../../kset-common/src/main/java/com/kset/common/event/config/KsetEventProperties.java) | `boolean` | `true` | — |
| [`executorBeanName`](../../kset-common/src/main/java/com/kset/common/event/config/KsetEventProperties.java) | `String` | — | Optional TaskExecutor bean name used by the local Spring event facade. |

### `KsetLifecycleProperties`

类型：class；配置前缀：`kset.lifecycle`；[源码](../../kset-common/src/main/java/com/kset/common/lifecycle/KsetLifecycleProperties.java)。

职责：kset 统一启停配置。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`enabled`](../../kset-common/src/main/java/com/kset/common/lifecycle/KsetLifecycleProperties.java) | `boolean` | `true` | 是否启用 kset 统一启停编排。 |
| [`shutdownTimeout`](../../kset-common/src/main/java/com/kset/common/lifecycle/KsetLifecycleProperties.java) | `Duration` | `Duration.ofSeconds(30)` | 各组件停机排空等待预算，超时后兜底强制关闭。 |

### `KsetLoggingProperties`

类型：class；配置前缀：`kset.logging`；[源码](../../kset-common/src/main/java/com/kset/common/logging/config/KsetLoggingProperties.java)。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`autoConfig`](../../kset-common/src/main/java/com/kset/common/logging/config/KsetLoggingProperties.java) | `boolean` | `true` | 是否自动注入 logging.config。 设为 false 时回退 Spring Boot 默认 logback 发现逻辑。 |
| [`defaultProfile`](../../kset-common/src/main/java/com/kset/common/logging/config/KsetLoggingProperties.java) | `String` | `DEFAULT_PROFILE` | 未设置 spring.profiles.active/default 时按此判定日志行为（仅日志层内部使用，不注入 spring.profiles.default） |
| [`businessDebug`](../../kset-common/src/main/java/com/kset/common/logging/config/KsetLoggingProperties.java) | `BusinessDebug` | `new BusinessDebug()` | 业务侧 DEBUG 配置 |

### `KsetLoggingProperties.BusinessDebug`

类型：class；[源码](../../kset-common/src/main/java/com/kset/common/logging/config/KsetLoggingProperties.java)。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`enabled`](../../kset-common/src/main/java/com/kset/common/logging/config/KsetLoggingProperties.java) | `boolean` | `DEFAULT_BUSINESS_DEBUG_ENABLED` | dev/default 默认开启；test/prod 由环境后处理器默认关闭 |
| [`packages`](../../kset-common/src/main/java/com/kset/common/logging/config/KsetLoggingProperties.java) | `String` | — | 业务侧 Logger 包名，多个包名用英文逗号分隔 |

### `CompletedTransaction`

类型：record；[源码](../../kset-common/src/main/java/com/kset/common/monitor/facade/CompletedTransaction.java)。

职责：已完成事务快照，供 com.kset.common.monitor.backend.MonitorBackend 上报。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`type`](../../kset-common/src/main/java/com/kset/common/monitor/facade/CompletedTransaction.java) | `String` | — | — |
| [`name`](../../kset-common/src/main/java/com/kset/common/monitor/facade/CompletedTransaction.java) | `String` | — | — |
| [`durationMs`](../../kset-common/src/main/java/com/kset/common/monitor/facade/CompletedTransaction.java) | `long` | — | — |
| [`status`](../../kset-common/src/main/java/com/kset/common/monitor/facade/CompletedTransaction.java) | `MonitorStatus` | — | — |
| [`data`](../../kset-common/src/main/java/com/kset/common/monitor/facade/CompletedTransaction.java) | `Map<String, String>` | — | — |
| [`traceId`](../../kset-common/src/main/java/com/kset/common/monitor/facade/CompletedTransaction.java) | `Optional<String>` | — | — |
| [`parentType`](../../kset-common/src/main/java/com/kset/common/monitor/facade/CompletedTransaction.java) | `Optional<String>` | — | — |

### `MonitorEvent`

类型：record；[源码](../../kset-common/src/main/java/com/kset/common/monitor/facade/MonitorEvent.java)。

职责：监控事件载荷（对齐 CAT Event）。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`type`](../../kset-common/src/main/java/com/kset/common/monitor/facade/MonitorEvent.java) | `String` | — | — |
| [`name`](../../kset-common/src/main/java/com/kset/common/monitor/facade/MonitorEvent.java) | `String` | — | — |
| [`status`](../../kset-common/src/main/java/com/kset/common/monitor/facade/MonitorEvent.java) | `MonitorStatus` | — | — |
| [`data`](../../kset-common/src/main/java/com/kset/common/monitor/facade/MonitorEvent.java) | `String` | — | — |
| [`traceId`](../../kset-common/src/main/java/com/kset/common/monitor/facade/MonitorEvent.java) | `Optional<String>` | — | — |

### `MonitorMetric`

类型：record；[源码](../../kset-common/src/main/java/com/kset/common/monitor/facade/MonitorMetric.java)。

职责：监控指标载荷（对齐 CAT Metric）。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`name`](../../kset-common/src/main/java/com/kset/common/monitor/facade/MonitorMetric.java) | `String` | — | — |
| [`value`](../../kset-common/src/main/java/com/kset/common/monitor/facade/MonitorMetric.java) | `long` | — | — |
| [`kind`](../../kset-common/src/main/java/com/kset/common/monitor/facade/MonitorMetric.java) | `MetricKind` | — | — |
| [`traceId`](../../kset-common/src/main/java/com/kset/common/monitor/facade/MonitorMetric.java) | `Optional<String>` | — | — |

### `InvocationContext`

类型：class；[源码](../../kset-common/src/main/java/com/kset/common/monitor/interceptor/InvocationContext.java)。

职责：框架调用上下文（供 FrameworkInterceptor 使用）。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`framework`](../../kset-common/src/main/java/com/kset/common/monitor/interceptor/InvocationContext.java) | `String` | — | — |
| [`type`](../../kset-common/src/main/java/com/kset/common/monitor/interceptor/InvocationContext.java) | `String` | — | — |
| [`name`](../../kset-common/src/main/java/com/kset/common/monitor/interceptor/InvocationContext.java) | `String` | — | — |
| [`startNanos`](../../kset-common/src/main/java/com/kset/common/monitor/interceptor/InvocationContext.java) | `long` | — | — |
| [`attributes`](../../kset-common/src/main/java/com/kset/common/monitor/interceptor/InvocationContext.java) | `Map<String, Object>` | — | — |

### `TraceSnapshot`

类型：class；[源码](../../kset-common/src/main/java/com/kset/common/monitor/TraceSnapshot.java)。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`traceId`](../../kset-common/src/main/java/com/kset/common/monitor/TraceSnapshot.java) | `String` | — | — |
| [`spanId`](../../kset-common/src/main/java/com/kset/common/monitor/TraceSnapshot.java) | `String` | — | — |
| [`grayTag`](../../kset-common/src/main/java/com/kset/common/monitor/TraceSnapshot.java) | `String` | — | — |

### `DrawEvent`

类型：record；[源码](../../kset-common/src/main/java/com/kset/common/utils/random/DrawEvent.java)。

职责：单次抽取事件，用于日志重放与审计。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`name`](../../kset-common/src/main/java/com/kset/common/utils/random/DrawEvent.java) | `String` | — | — |
| [`seq`](../../kset-common/src/main/java/com/kset/common/utils/random/DrawEvent.java) | `long` | — | — |
| [`key`](../../kset-common/src/main/java/com/kset/common/utils/random/DrawEvent.java) | `String` | — | — |
| [`drawTimeMs`](../../kset-common/src/main/java/com/kset/common/utils/random/DrawEvent.java) | `long` | — | — |
| [`hiddenKeys`](../../kset-common/src/main/java/com/kset/common/utils/random/DrawEvent.java) | `List<String>` | — | — |
| [`configVersion`](../../kset-common/src/main/java/com/kset/common/utils/random/DrawEvent.java) | `int` | — | — |
| [`algorithm`](../../kset-common/src/main/java/com/kset/common/utils/random/DrawEvent.java) | `WeightedRandomAlgorithm` | — | — |
| [`seed`](../../kset-common/src/main/java/com/kset/common/utils/random/DrawEvent.java) | `Long` | — | — |

### `WeightedRandomConfig`

类型：class；[源码](../../kset-common/src/main/java/com/kset/common/utils/random/WeightedRandomConfig.java)。

职责：加权随机引擎配置。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`name`](../../kset-common/src/main/java/com/kset/common/utils/random/WeightedRandomConfig.java) | `String` | — | — |
| [`weights`](../../kset-common/src/main/java/com/kset/common/utils/random/WeightedRandomConfig.java) | `Map<String, Double>` | `new LinkedHashMap<>()` | — |
| [`items`](../../kset-common/src/main/java/com/kset/common/utils/random/WeightedRandomConfig.java) | `List<String>` | — | — |
| [`algorithm`](../../kset-common/src/main/java/com/kset/common/utils/random/WeightedRandomConfig.java) | `WeightedRandomAlgorithm` | — | — |
| [`seed`](../../kset-common/src/main/java/com/kset/common/utils/random/WeightedRandomConfig.java) | `Long` | — | — |
| [`sampleBase`](../../kset-common/src/main/java/com/kset/common/utils/random/WeightedRandomConfig.java) | `int` | `DEFAULT_SAMPLE_BASE` | — |
| [`metricsEnabled`](../../kset-common/src/main/java/com/kset/common/utils/random/WeightedRandomConfig.java) | `boolean` | `true` | — |
| [`replayEnabled`](../../kset-common/src/main/java/com/kset/common/utils/random/WeightedRandomConfig.java) | `boolean` | `false` | — |
| [`journalEnabled`](../../kset-common/src/main/java/com/kset/common/utils/random/WeightedRandomConfig.java) | `boolean` | `false` | — |
| [`journalBufferSize`](../../kset-common/src/main/java/com/kset/common/utils/random/WeightedRandomConfig.java) | `int` | `DEFAULT_JOURNAL_BUFFER_SIZE` | — |

### `WeightedRandomMetrics`

类型：class；[源码](../../kset-common/src/main/java/com/kset/common/utils/random/WeightedRandomMetrics.java)。

职责：加权随机运行时指标快照。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`name`](../../kset-common/src/main/java/com/kset/common/utils/random/WeightedRandomMetrics.java) | `String` | — | — |
| [`algorithm`](../../kset-common/src/main/java/com/kset/common/utils/random/WeightedRandomMetrics.java) | `WeightedRandomAlgorithm` | — | — |
| [`totalDraws`](../../kset-common/src/main/java/com/kset/common/utils/random/WeightedRandomMetrics.java) | `long` | — | — |
| [`seed`](../../kset-common/src/main/java/com/kset/common/utils/random/WeightedRandomMetrics.java) | `Long` | — | — |
| [`configVersion`](../../kset-common/src/main/java/com/kset/common/utils/random/WeightedRandomMetrics.java) | `int` | — | — |
| [`snapshotTimeMs`](../../kset-common/src/main/java/com/kset/common/utils/random/WeightedRandomMetrics.java) | `long` | — | — |
| [`maxDeviation`](../../kset-common/src/main/java/com/kset/common/utils/random/WeightedRandomMetrics.java) | `double` | — | — |
| [`chiSquare`](../../kset-common/src/main/java/com/kset/common/utils/random/WeightedRandomMetrics.java) | `double` | — | — |
| [`items`](../../kset-common/src/main/java/com/kset/common/utils/random/WeightedRandomMetrics.java) | `List<ItemMetric>` | `new ArrayList<>()` | — |

### `WeightedRandomMetrics.ItemMetric`

类型：class；[源码](../../kset-common/src/main/java/com/kset/common/utils/random/WeightedRandomMetrics.java)。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`key`](../../kset-common/src/main/java/com/kset/common/utils/random/WeightedRandomMetrics.java) | `String` | — | — |
| [`count`](../../kset-common/src/main/java/com/kset/common/utils/random/WeightedRandomMetrics.java) | `long` | — | — |
| [`weight`](../../kset-common/src/main/java/com/kset/common/utils/random/WeightedRandomMetrics.java) | `double` | — | — |
| [`expectedRate`](../../kset-common/src/main/java/com/kset/common/utils/random/WeightedRandomMetrics.java) | `double` | — | — |
| [`actualRate`](../../kset-common/src/main/java/com/kset/common/utils/random/WeightedRandomMetrics.java) | `double` | — | — |
| [`deviation`](../../kset-common/src/main/java/com/kset/common/utils/random/WeightedRandomMetrics.java) | `double` | — | — |

### `KsetThreadPoolFactory.PoolConfig`

类型：class；[源码](../../kset-common/src/main/java/com/kset/common/utils/thread/KsetThreadPoolFactory.java)。

职责：// ========== 配置类 ==========

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`corePoolSize`](../../kset-common/src/main/java/com/kset/common/utils/thread/KsetThreadPoolFactory.java) | `int` | `32` | — |
| [`maximumPoolSize`](../../kset-common/src/main/java/com/kset/common/utils/thread/KsetThreadPoolFactory.java) | `int` | `200` | — |
| [`keepAliveTimeMs`](../../kset-common/src/main/java/com/kset/common/utils/thread/KsetThreadPoolFactory.java) | `long` | `4000L` | — |
| [`queueCapacity`](../../kset-common/src/main/java/com/kset/common/utils/thread/KsetThreadPoolFactory.java) | `int` | `100` | — |
| [`targetLatencyMs`](../../kset-common/src/main/java/com/kset/common/utils/thread/KsetThreadPoolFactory.java) | `long` | `1000L` | — |
| [`autoTuneEnabled`](../../kset-common/src/main/java/com/kset/common/utils/thread/KsetThreadPoolFactory.java) | `boolean` | `false` | — |
| [`tuneIntervalMs`](../../kset-common/src/main/java/com/kset/common/utils/thread/KsetThreadPoolFactory.java) | `long` | `10000L` | — |
| [`priorityQueue`](../../kset-common/src/main/java/com/kset/common/utils/thread/KsetThreadPoolFactory.java) | `boolean` | `false` | — |
| [`defaultPriority`](../../kset-common/src/main/java/com/kset/common/utils/thread/KsetThreadPoolFactory.java) | `int` | `5` | — |
| [`rejectedHandler`](../../kset-common/src/main/java/com/kset/common/utils/thread/KsetThreadPoolFactory.java) | `RejectedExecutionHandler` | — | — |
| [`reporter`](../../kset-common/src/main/java/com/kset/common/utils/thread/KsetThreadPoolFactory.java) | `ThreadPoolReporter` | — | — |
| [`traceContextAdapter`](../../kset-common/src/main/java/com/kset/common/utils/thread/KsetThreadPoolFactory.java) | `ThreadPoolTraceAdapter` | — | — |

### `ThreadPoolMetrics`

类型：class；[源码](../../kset-common/src/main/java/com/kset/common/utils/thread/ThreadPoolMetrics.java)。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`poolName`](../../kset-common/src/main/java/com/kset/common/utils/thread/ThreadPoolMetrics.java) | `String` | — | // === 配置 === |
| [`corePoolSize`](../../kset-common/src/main/java/com/kset/common/utils/thread/ThreadPoolMetrics.java) | `int` | — | — |
| [`maximumPoolSize`](../../kset-common/src/main/java/com/kset/common/utils/thread/ThreadPoolMetrics.java) | `int` | — | — |
| [`keepAliveTimeMs`](../../kset-common/src/main/java/com/kset/common/utils/thread/ThreadPoolMetrics.java) | `long` | — | — |
| [`queueCapacity`](../../kset-common/src/main/java/com/kset/common/utils/thread/ThreadPoolMetrics.java) | `int` | — | — |
| [`autoTuneEnabled`](../../kset-common/src/main/java/com/kset/common/utils/thread/ThreadPoolMetrics.java) | `boolean` | — | — |
| [`targetLatencyMs`](../../kset-common/src/main/java/com/kset/common/utils/thread/ThreadPoolMetrics.java) | `long` | — | — |
| [`priorityQueueEnabled`](../../kset-common/src/main/java/com/kset/common/utils/thread/ThreadPoolMetrics.java) | `boolean` | — | — |
| [`defaultPriority`](../../kset-common/src/main/java/com/kset/common/utils/thread/ThreadPoolMetrics.java) | `int` | — | — |
| [`poolSize`](../../kset-common/src/main/java/com/kset/common/utils/thread/ThreadPoolMetrics.java) | `int` | — | // === 实时状态 === |
| [`activeCount`](../../kset-common/src/main/java/com/kset/common/utils/thread/ThreadPoolMetrics.java) | `int` | — | — |
| [`largestPoolSize`](../../kset-common/src/main/java/com/kset/common/utils/thread/ThreadPoolMetrics.java) | `int` | — | — |
| [`queueSize`](../../kset-common/src/main/java/com/kset/common/utils/thread/ThreadPoolMetrics.java) | `int` | — | — |
| [`queueRemainingCapacity`](../../kset-common/src/main/java/com/kset/common/utils/thread/ThreadPoolMetrics.java) | `int` | — | — |
| [`submittedTasks`](../../kset-common/src/main/java/com/kset/common/utils/thread/ThreadPoolMetrics.java) | `long` | — | // === 累计指标 === |
| [`completedTasks`](../../kset-common/src/main/java/com/kset/common/utils/thread/ThreadPoolMetrics.java) | `long` | — | — |
| [`failedTasks`](../../kset-common/src/main/java/com/kset/common/utils/thread/ThreadPoolMetrics.java) | `long` | — | — |
| [`rejectedTasks`](../../kset-common/src/main/java/com/kset/common/utils/thread/ThreadPoolMetrics.java) | `long` | — | — |
| [`successRate`](../../kset-common/src/main/java/com/kset/common/utils/thread/ThreadPoolMetrics.java) | `double` | — | 成功率 [0.0, 1.0] |
| [`avgExecutionTimeMs`](../../kset-common/src/main/java/com/kset/common/utils/thread/ThreadPoolMetrics.java) | `double` | — | 平均执行耗时（毫秒） |
| [`avgWaitTimeMs`](../../kset-common/src/main/java/com/kset/common/utils/thread/ThreadPoolMetrics.java) | `double` | — | 平均等待耗时（毫秒） |
| [`maxExecutionTimeMs`](../../kset-common/src/main/java/com/kset/common/utils/thread/ThreadPoolMetrics.java) | `long` | — | 最大执行耗时（毫秒） |
| [`minExecutionTimeMs`](../../kset-common/src/main/java/com/kset/common/utils/thread/ThreadPoolMetrics.java) | `long` | — | 最小执行耗时（毫秒） |
| [`p99ExecutionTimeMs`](../../kset-common/src/main/java/com/kset/common/utils/thread/ThreadPoolMetrics.java) | `double` | — | P99 执行耗时（毫秒） |
| [`throughputPerSecond`](../../kset-common/src/main/java/com/kset/common/utils/thread/ThreadPoolMetrics.java) | `double` | — | 吞吐量（任务/秒） |
| [`latencyErrorRatio`](../../kset-common/src/main/java/com/kset/common/utils/thread/ThreadPoolMetrics.java) | `double` | — | 延迟误差比例 = (实际平均耗时 - 目标耗时) / 目标耗时 |
| [`lastTuneTimeMs`](../../kset-common/src/main/java/com/kset/common/utils/thread/ThreadPoolMetrics.java) | `long` | — | — |
| [`lastTuneAction`](../../kset-common/src/main/java/com/kset/common/utils/thread/ThreadPoolMetrics.java) | `String` | — | — |

## kset-starter-auth

### `KsetAuthProperties`

类型：class；配置前缀：`kset.auth`；[源码](../../kset-starter-auth/src/main/java/com/kset/auth/config/KsetAuthProperties.java)。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`enabled`](../../kset-starter-auth/src/main/java/com/kset/auth/config/KsetAuthProperties.java) | `boolean` | `true` | — |
| [`defaultSubject`](../../kset-starter-auth/src/main/java/com/kset/auth/config/KsetAuthProperties.java) | `String` | `"app"` | — |
| [`defaultScheme`](../../kset-starter-auth/src/main/java/com/kset/auth/config/KsetAuthProperties.java) | `String` | `"session"` | — |
| [`defaultTokenHeader`](../../kset-starter-auth/src/main/java/com/kset/auth/config/KsetAuthProperties.java) | `String` | `AuthHeaders.SESSION_TOKEN` | — |
| [`subjectHeader`](../../kset-starter-auth/src/main/java/com/kset/auth/config/KsetAuthProperties.java) | `String` | `AuthHeaders.AUTH_SUBJECT` | — |
| [`rules`](../../kset-starter-auth/src/main/java/com/kset/auth/config/KsetAuthProperties.java) | `List<AuthRule>` | `new ArrayList<>()` | — |
| [`session`](../../kset-starter-auth/src/main/java/com/kset/auth/config/KsetAuthProperties.java) | `Session` | `new Session()` | — |
| [`appKey`](../../kset-starter-auth/src/main/java/com/kset/auth/config/KsetAuthProperties.java) | `AppKey` | `new AppKey()` | — |
| [`web`](../../kset-starter-auth/src/main/java/com/kset/auth/config/KsetAuthProperties.java) | `Web` | `new Web()` | — |
| [`gateway`](../../kset-starter-auth/src/main/java/com/kset/auth/config/KsetAuthProperties.java) | `Gateway` | `new Gateway()` | — |
| [`dubbo`](../../kset-starter-auth/src/main/java/com/kset/auth/config/KsetAuthProperties.java) | `Dubbo` | `new Dubbo()` | — |

### `KsetAuthProperties.Session`

类型：class；[源码](../../kset-starter-auth/src/main/java/com/kset/auth/config/KsetAuthProperties.java)。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`storeType`](../../kset-starter-auth/src/main/java/com/kset/auth/config/KsetAuthProperties.java) | `StoreType` | `StoreType.AUTO` | — |
| [`keyPrefix`](../../kset-starter-auth/src/main/java/com/kset/auth/config/KsetAuthProperties.java) | `String` | `"kset:auth:session:"` | — |
| [`ttl`](../../kset-starter-auth/src/main/java/com/kset/auth/config/KsetAuthProperties.java) | `Duration` | `Duration.ofHours(2)` | — |
| [`slidingRefreshEnabled`](../../kset-starter-auth/src/main/java/com/kset/auth/config/KsetAuthProperties.java) | `boolean` | `true` | — |
| [`refreshThreshold`](../../kset-starter-auth/src/main/java/com/kset/auth/config/KsetAuthProperties.java) | `Duration` | `Duration.ofMinutes(30)` | — |

### `KsetAuthProperties.AppKey`

类型：class；[源码](../../kset-starter-auth/src/main/java/com/kset/auth/config/KsetAuthProperties.java)。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`enabled`](../../kset-starter-auth/src/main/java/com/kset/auth/config/KsetAuthProperties.java) | `boolean` | `true` | — |
| [`appKeyHeader`](../../kset-starter-auth/src/main/java/com/kset/auth/config/KsetAuthProperties.java) | `String` | `"X-App-Key"` | — |
| [`tokenHeader`](../../kset-starter-auth/src/main/java/com/kset/auth/config/KsetAuthProperties.java) | `String` | `"X-App-Token"` | — |
| [`signHeader`](../../kset-starter-auth/src/main/java/com/kset/auth/config/KsetAuthProperties.java) | `String` | `"X-Sign"` | — |
| [`timestampHeader`](../../kset-starter-auth/src/main/java/com/kset/auth/config/KsetAuthProperties.java) | `String` | `"X-Timestamp"` | — |
| [`nonceHeader`](../../kset-starter-auth/src/main/java/com/kset/auth/config/KsetAuthProperties.java) | `String` | `"X-Nonce"` | — |
| [`appKeyField`](../../kset-starter-auth/src/main/java/com/kset/auth/config/KsetAuthProperties.java) | `String` | `"appKey"` | — |
| [`signField`](../../kset-starter-auth/src/main/java/com/kset/auth/config/KsetAuthProperties.java) | `String` | `"sign"` | — |
| [`algorithm`](../../kset-starter-auth/src/main/java/com/kset/auth/config/KsetAuthProperties.java) | `String` | `"sha1"` | — |
| [`timestampRequired`](../../kset-starter-auth/src/main/java/com/kset/auth/config/KsetAuthProperties.java) | `boolean` | `false` | — |
| [`timestampTtl`](../../kset-starter-auth/src/main/java/com/kset/auth/config/KsetAuthProperties.java) | `Duration` | `Duration.ofMinutes(5)` | — |
| [`apps`](../../kset-starter-auth/src/main/java/com/kset/auth/config/KsetAuthProperties.java) | `List<App>` | `new ArrayList<>()` | — |

### `KsetAuthProperties.App`

类型：class；[源码](../../kset-starter-auth/src/main/java/com/kset/auth/config/KsetAuthProperties.java)。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`enabled`](../../kset-starter-auth/src/main/java/com/kset/auth/config/KsetAuthProperties.java) | `boolean` | `true` | — |
| [`appKey`](../../kset-starter-auth/src/main/java/com/kset/auth/config/KsetAuthProperties.java) | `String` | — | — |
| [`secret`](../../kset-starter-auth/src/main/java/com/kset/auth/config/KsetAuthProperties.java) | `String` | — | — |
| [`token`](../../kset-starter-auth/src/main/java/com/kset/auth/config/KsetAuthProperties.java) | `String` | — | — |
| [`subject`](../../kset-starter-auth/src/main/java/com/kset/auth/config/KsetAuthProperties.java) | `String` | — | — |
| [`userId`](../../kset-starter-auth/src/main/java/com/kset/auth/config/KsetAuthProperties.java) | `String` | — | — |
| [`userName`](../../kset-starter-auth/src/main/java/com/kset/auth/config/KsetAuthProperties.java) | `String` | — | — |
| [`roles`](../../kset-starter-auth/src/main/java/com/kset/auth/config/KsetAuthProperties.java) | `List<String>` | `new ArrayList<>()` | — |
| [`permissions`](../../kset-starter-auth/src/main/java/com/kset/auth/config/KsetAuthProperties.java) | `List<String>` | `new ArrayList<>()` | — |

### `KsetAuthProperties.Web`

类型：class；[源码](../../kset-starter-auth/src/main/java/com/kset/auth/config/KsetAuthProperties.java)。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`enabled`](../../kset-starter-auth/src/main/java/com/kset/auth/config/KsetAuthProperties.java) | `boolean` | `true` | — |
| [`mode`](../../kset-starter-auth/src/main/java/com/kset/auth/config/KsetAuthProperties.java) | `Mode` | `Mode.REDIS` | — |
| [`tokenHeader`](../../kset-starter-auth/src/main/java/com/kset/auth/config/KsetAuthProperties.java) | `String` | `AuthHeaders.SESSION_TOKEN` | — |
| [`legacySplitHeadersEnabled`](../../kset-starter-auth/src/main/java/com/kset/auth/config/KsetAuthProperties.java) | `boolean` | `false` | 是否接受 X-User-Id 等遗留分头。默认关闭；仅网关后的兼容期可打开，且仍不从分头读取角色权限。 |
| [`publicPaths`](../../kset-starter-auth/src/main/java/com/kset/auth/config/KsetAuthProperties.java) | `List<String>` | `new ArrayList<>(List.of(                 "/api/public/**",                 "/actuator/health/**"))` | — |

### `KsetAuthProperties.Gateway`

类型：class；[源码](../../kset-starter-auth/src/main/java/com/kset/auth/config/KsetAuthProperties.java)。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`enabled`](../../kset-starter-auth/src/main/java/com/kset/auth/config/KsetAuthProperties.java) | `boolean` | `true` | — |
| [`tokenHeader`](../../kset-starter-auth/src/main/java/com/kset/auth/config/KsetAuthProperties.java) | `String` | `AuthHeaders.SESSION_TOKEN` | — |
| [`publicPaths`](../../kset-starter-auth/src/main/java/com/kset/auth/config/KsetAuthProperties.java) | `List<String>` | `new ArrayList<>(List.of(                 "/api/public/**",                 "/actuator/health/**"))` | — |

### `KsetAuthProperties.Dubbo`

类型：class；[源码](../../kset-starter-auth/src/main/java/com/kset/auth/config/KsetAuthProperties.java)。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`enabled`](../../kset-starter-auth/src/main/java/com/kset/auth/config/KsetAuthProperties.java) | `boolean` | `true` | — |
| [`propagateToken`](../../kset-starter-auth/src/main/java/com/kset/auth/config/KsetAuthProperties.java) | `boolean` | `false` | — |

### `KsetAuthProperties.AuthRule`

类型：class；[源码](../../kset-starter-auth/src/main/java/com/kset/auth/config/KsetAuthProperties.java)。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`name`](../../kset-starter-auth/src/main/java/com/kset/auth/config/KsetAuthProperties.java) | `String` | — | — |
| [`paths`](../../kset-starter-auth/src/main/java/com/kset/auth/config/KsetAuthProperties.java) | `List<String>` | `new ArrayList<>()` | — |
| [`subject`](../../kset-starter-auth/src/main/java/com/kset/auth/config/KsetAuthProperties.java) | `String` | — | — |
| [`scheme`](../../kset-starter-auth/src/main/java/com/kset/auth/config/KsetAuthProperties.java) | `String` | — | — |
| [`tokenHeader`](../../kset-starter-auth/src/main/java/com/kset/auth/config/KsetAuthProperties.java) | `String` | — | — |
| [`trustedHeaderName`](../../kset-starter-auth/src/main/java/com/kset/auth/config/KsetAuthProperties.java) | `String` | — | — |

### `AuthRequest`

类型：class；[源码](../../kset-starter-auth/src/main/java/com/kset/auth/core/AuthRequest.java)。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`path`](../../kset-starter-auth/src/main/java/com/kset/auth/core/AuthRequest.java) | `String` | — | — |
| [`headerReader`](../../kset-starter-auth/src/main/java/com/kset/auth/core/AuthRequest.java) | `LoginUserHeaderCodec.HeaderReader` | — | — |
| [`source`](../../kset-starter-auth/src/main/java/com/kset/auth/core/AuthRequest.java) | `String` | — | — |
| [`method`](../../kset-starter-auth/src/main/java/com/kset/auth/core/AuthRequest.java) | `String` | — | — |
| [`params`](../../kset-starter-auth/src/main/java/com/kset/auth/core/AuthRequest.java) | `Map<String, String>` | — | — |

### `AuthResult`

类型：class；[源码](../../kset-starter-auth/src/main/java/com/kset/auth/core/AuthResult.java)。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`authenticated`](../../kset-starter-auth/src/main/java/com/kset/auth/core/AuthResult.java) | `boolean` | — | — |
| [`permitAll`](../../kset-starter-auth/src/main/java/com/kset/auth/core/AuthResult.java) | `boolean` | — | — |
| [`user`](../../kset-starter-auth/src/main/java/com/kset/auth/core/AuthResult.java) | `LoginUser` | — | — |
| [`code`](../../kset-starter-auth/src/main/java/com/kset/auth/core/AuthResult.java) | `int` | — | — |
| [`message`](../../kset-starter-auth/src/main/java/com/kset/auth/core/AuthResult.java) | `String` | — | — |

### `DefaultLoginUserHeaderCodec.BasicLoginContext`

类型：class；[源码](../../kset-starter-auth/src/main/java/com/kset/auth/core/DefaultLoginUserHeaderCodec.java)。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`userId`](../../kset-starter-auth/src/main/java/com/kset/auth/core/DefaultLoginUserHeaderCodec.java) | `String` | — | — |
| [`subjectType`](../../kset-starter-auth/src/main/java/com/kset/auth/core/DefaultLoginUserHeaderCodec.java) | `String` | — | — |
| [`userName`](../../kset-starter-auth/src/main/java/com/kset/auth/core/DefaultLoginUserHeaderCodec.java) | `String` | — | — |
| [`tenantId`](../../kset-starter-auth/src/main/java/com/kset/auth/core/DefaultLoginUserHeaderCodec.java) | `String` | — | — |
| [`orgId`](../../kset-starter-auth/src/main/java/com/kset/auth/core/DefaultLoginUserHeaderCodec.java) | `String` | — | — |
| [`deptId`](../../kset-starter-auth/src/main/java/com/kset/auth/core/DefaultLoginUserHeaderCodec.java) | `String` | — | — |
| [`roles`](../../kset-starter-auth/src/main/java/com/kset/auth/core/DefaultLoginUserHeaderCodec.java) | `List<String>` | `List.of()` | — |
| [`permissions`](../../kset-starter-auth/src/main/java/com/kset/auth/core/DefaultLoginUserHeaderCodec.java) | `List<String>` | `List.of()` | — |
| [`token`](../../kset-starter-auth/src/main/java/com/kset/auth/core/DefaultLoginUserHeaderCodec.java) | `String` | — | — |

## kset-starter-cache

### `KsetCacheProperties`

类型：class；配置前缀：`kset.cache`；[源码](../../kset-starter-cache/src/main/java/com/kset/cache/config/KsetCacheProperties.java)。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`enabled`](../../kset-starter-cache/src/main/java/com/kset/cache/config/KsetCacheProperties.java) | `boolean` | `true` | 是否启用缓存组件。 |
| [`defaultLayers`](../../kset-starter-cache/src/main/java/com/kset/cache/config/KsetCacheProperties.java) | `List<KsetCacheLayer>` | `new ArrayList<>(List.of(KsetCacheLayer.L1))` | 未在注解中指定 layers 时使用的默认缓存层级。 |
| [`cacheNull`](../../kset-starter-cache/src/main/java/com/kset/cache/config/KsetCacheProperties.java) | `boolean` | `true` | 默认是否缓存 null 结果，避免热点空值反复穿透到后端。 |
| [`nullTtl`](../../kset-starter-cache/src/main/java/com/kset/cache/config/KsetCacheProperties.java) | `Duration` | `Duration.ofMinutes(1)` | null 结果默认 TTL。 |
| [`singleFlightEnabled`](../../kset-starter-cache/src/main/java/com/kset/cache/config/KsetCacheProperties.java) | `boolean` | `true` | 是否启用同 key 加载合并，降低缓存击穿时的并发回源。 |
| [`ttlJitterEnabled`](../../kset-starter-cache/src/main/java/com/kset/cache/config/KsetCacheProperties.java) | `boolean` | `false` | 是否为最终 TTL 增加随机抖动，降低同批 key 同时过期概率。 |
| [`ttlJitterPercent`](../../kset-starter-cache/src/main/java/com/kset/cache/config/KsetCacheProperties.java) | `int` | `10` | TTL 抖动百分比，开启后在原 TTL 基础上随机增加 0 到该百分比。 |
| [`l1`](../../kset-starter-cache/src/main/java/com/kset/cache/config/KsetCacheProperties.java) | `L1` | `new L1()` | — |
| [`l2`](../../kset-starter-cache/src/main/java/com/kset/cache/config/KsetCacheProperties.java) | `L2` | `new L2()` | — |

### `KsetCacheProperties.L1`

类型：class；[源码](../../kset-starter-cache/src/main/java/com/kset/cache/config/KsetCacheProperties.java)。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`enabled`](../../kset-starter-cache/src/main/java/com/kset/cache/config/KsetCacheProperties.java) | `boolean` | `true` | 是否启用本地 L1 缓存。 |
| [`defaultTtl`](../../kset-starter-cache/src/main/java/com/kset/cache/config/KsetCacheProperties.java) | `Duration` | `Duration.ofMinutes(5)` | L1 默认 TTL。 |
| [`maximumSize`](../../kset-starter-cache/src/main/java/com/kset/cache/config/KsetCacheProperties.java) | `long` | `10_000` | L1 本地缓存最大条目数。 |
| [`initialCapacity`](../../kset-starter-cache/src/main/java/com/kset/cache/config/KsetCacheProperties.java) | `int` | `128` | L1 本地缓存初始容量。 |
| [`recordStats`](../../kset-starter-cache/src/main/java/com/kset/cache/config/KsetCacheProperties.java) | `boolean` | `false` | 是否启用 Caffeine 统计。 |

### `KsetCacheProperties.L2`

类型：class；[源码](../../kset-starter-cache/src/main/java/com/kset/cache/config/KsetCacheProperties.java)。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`defaultTtl`](../../kset-starter-cache/src/main/java/com/kset/cache/config/KsetCacheProperties.java) | `Duration` | `Duration.ofMinutes(30)` | L2 默认 TTL。 |

### `KsetCacheMetrics`

类型：record；[源码](../../kset-starter-cache/src/main/java/com/kset/cache/core/KsetCacheMetrics.java)。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`l1Hits`](../../kset-starter-cache/src/main/java/com/kset/cache/core/KsetCacheMetrics.java) | `long` | — | — |
| [`l2Hits`](../../kset-starter-cache/src/main/java/com/kset/cache/core/KsetCacheMetrics.java) | `long` | — | — |
| [`misses`](../../kset-starter-cache/src/main/java/com/kset/cache/core/KsetCacheMetrics.java) | `long` | — | — |
| [`loads`](../../kset-starter-cache/src/main/java/com/kset/cache/core/KsetCacheMetrics.java) | `long` | — | — |
| [`puts`](../../kset-starter-cache/src/main/java/com/kset/cache/core/KsetCacheMetrics.java) | `long` | — | — |
| [`evicts`](../../kset-starter-cache/src/main/java/com/kset/cache/core/KsetCacheMetrics.java) | `long` | — | — |
| [`errors`](../../kset-starter-cache/src/main/java/com/kset/cache/core/KsetCacheMetrics.java) | `long` | — | — |

### `KsetCacheSpec`

类型：class；[源码](../../kset-starter-cache/src/main/java/com/kset/cache/core/KsetCacheSpec.java)。

职责：单次缓存操作的完整规格，包含 key、层级、TTL 与空值策略。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`cacheName`](../../kset-starter-cache/src/main/java/com/kset/cache/core/KsetCacheSpec.java) | `String` | — | — |
| [`key`](../../kset-starter-cache/src/main/java/com/kset/cache/core/KsetCacheSpec.java) | `String` | — | — |
| [`layers`](../../kset-starter-cache/src/main/java/com/kset/cache/core/KsetCacheSpec.java) | `List<KsetCacheLayer>` | — | — |
| [`ttl`](../../kset-starter-cache/src/main/java/com/kset/cache/core/KsetCacheSpec.java) | `Duration` | — | — |
| [`nullTtl`](../../kset-starter-cache/src/main/java/com/kset/cache/core/KsetCacheSpec.java) | `Duration` | — | — |
| [`cacheNull`](../../kset-starter-cache/src/main/java/com/kset/cache/core/KsetCacheSpec.java) | `boolean` | — | — |
| [`valueType`](../../kset-starter-cache/src/main/java/com/kset/cache/core/KsetCacheSpec.java) | `Class<?>` | — | — |

## kset-starter-datasource

### `KsetDatasourceProperties`

类型：class；配置前缀：`kset.datasource`；[源码](../../kset-starter-datasource/src/main/java/com/kset/datasource/config/KsetDatasourceProperties.java)。

职责：数据源组件开关。 <p>MyBatis-Plus、Flyway 和 dynamic-datasource 均优先使用开源组件原生配置： mybatis-plus.*、spring.flyway.*、spring.datasource.dynamic.*。</p>

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`enabled`](../../kset-starter-datasource/src/main/java/com/kset/datasource/config/KsetDatasourceProperties.java) | `boolean` | `true` | — |
| [`autoFill`](../../kset-starter-datasource/src/main/java/com/kset/datasource/config/KsetDatasourceProperties.java) | `boolean` | `true` | 是否注册 createTime / updateTime、createdAt / updatedAt、createDate / updateDate 自动填充处理器。 |

## kset-starter-dubbo

### `DubboRouteRuleHolder.RouteRuleConfig`

类型：class；[源码](../../kset-starter-dubbo/src/main/java/com/kset/dubbo/route/DubboRouteRuleHolder.java)。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`conditions`](../../kset-starter-dubbo/src/main/java/com/kset/dubbo/route/DubboRouteRuleHolder.java) | `List<RouteCondition>` | `new ArrayList<>()` | — |

## kset-starter-monitor

### `KsetMonitorProperties`

类型：class；配置前缀：`kset.monitor`；[源码](../../kset-starter-monitor/src/main/java/com/kset/common/monitor/config/KsetMonitorProperties.java)。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`enabled`](../../kset-starter-monitor/src/main/java/com/kset/common/monitor/config/KsetMonitorProperties.java) | `boolean` | `true` | — |
| [`backend`](../../kset-starter-monitor/src/main/java/com/kset/common/monitor/config/KsetMonitorProperties.java) | `String` | `"log"` | — |
| [`cat`](../../kset-starter-monitor/src/main/java/com/kset/common/monitor/config/KsetMonitorProperties.java) | `Cat` | `new Cat()` | — |
| [`sampler`](../../kset-starter-monitor/src/main/java/com/kset/common/monitor/config/KsetMonitorProperties.java) | `SamplerConfig` | `new SamplerConfig()` | — |
| [`reporter`](../../kset-starter-monitor/src/main/java/com/kset/common/monitor/config/KsetMonitorProperties.java) | `Reporter` | `new Reporter()` | — |
| [`web`](../../kset-starter-monitor/src/main/java/com/kset/common/monitor/config/KsetMonitorProperties.java) | `Web` | `new Web()` | — |
| [`servlet`](../../kset-starter-monitor/src/main/java/com/kset/common/monitor/config/KsetMonitorProperties.java) | `Servlet` | `new Servlet()` | — |
| [`dubbo`](../../kset-starter-monitor/src/main/java/com/kset/common/monitor/config/KsetMonitorProperties.java) | `Dubbo` | `new Dubbo()` | — |
| [`gateway`](../../kset-starter-monitor/src/main/java/com/kset/common/monitor/config/KsetMonitorProperties.java) | `Gateway` | `new Gateway()` | — |
| [`mybatis`](../../kset-starter-monitor/src/main/java/com/kset/common/monitor/config/KsetMonitorProperties.java) | `Mybatis` | `new Mybatis()` | — |
| [`httpClient`](../../kset-starter-monitor/src/main/java/com/kset/common/monitor/config/KsetMonitorProperties.java) | `HttpClient` | `new HttpClient()` | — |
| [`redis`](../../kset-starter-monitor/src/main/java/com/kset/common/monitor/config/KsetMonitorProperties.java) | `Redis` | `new Redis()` | — |
| [`threadPool`](../../kset-starter-monitor/src/main/java/com/kset/common/monitor/config/KsetMonitorProperties.java) | `ThreadPool` | `new ThreadPool()` | — |
| [`async`](../../kset-starter-monitor/src/main/java/com/kset/common/monitor/config/KsetMonitorProperties.java) | `Async` | `new Async()` | — |
| [`scheduled`](../../kset-starter-monitor/src/main/java/com/kset/common/monitor/config/KsetMonitorProperties.java) | `Scheduled` | `new Scheduled()` | — |

### `KsetMonitorProperties.Web`

类型：class；[源码](../../kset-starter-monitor/src/main/java/com/kset/common/monitor/config/KsetMonitorProperties.java)。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`enabled`](../../kset-starter-monitor/src/main/java/com/kset/common/monitor/config/KsetMonitorProperties.java) | `boolean` | `true` | — |

### `KsetMonitorProperties.Cat`

类型：class；[源码](../../kset-starter-monitor/src/main/java/com/kset/common/monitor/config/KsetMonitorProperties.java)。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`initialize`](../../kset-starter-monitor/src/main/java/com/kset/common/monitor/config/KsetMonitorProperties.java) | `boolean` | `false` | — |
| [`domain`](../../kset-starter-monitor/src/main/java/com/kset/common/monitor/config/KsetMonitorProperties.java) | `String` | — | — |

### `KsetMonitorProperties.Servlet`

类型：class；[源码](../../kset-starter-monitor/src/main/java/com/kset/common/monitor/config/KsetMonitorProperties.java)。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`traceEnabled`](../../kset-starter-monitor/src/main/java/com/kset/common/monitor/config/KsetMonitorProperties.java) | `boolean` | `true` | — |
| [`grayTagEnabled`](../../kset-starter-monitor/src/main/java/com/kset/common/monitor/config/KsetMonitorProperties.java) | `boolean` | `true` | — |

### `KsetMonitorProperties.Dubbo`

类型：class；[源码](../../kset-starter-monitor/src/main/java/com/kset/common/monitor/config/KsetMonitorProperties.java)。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`enabled`](../../kset-starter-monitor/src/main/java/com/kset/common/monitor/config/KsetMonitorProperties.java) | `boolean` | `true` | — |
| [`tracePropagationEnabled`](../../kset-starter-monitor/src/main/java/com/kset/common/monitor/config/KsetMonitorProperties.java) | `Boolean` | — | Dubbo 链路透传开关；未设置时回退兼容键 kset.cloud.dubbo.trace-propagation-enabled |
| [`defaultGrayTag`](../../kset-starter-monitor/src/main/java/com/kset/common/monitor/config/KsetMonitorProperties.java) | `String` | — | 请求未携带灰度标签时的兜底标签；未设置时回退兼容键 kset.cloud.dubbo.default-gray-tag |

### `KsetMonitorProperties.Gateway`

类型：class；[源码](../../kset-starter-monitor/src/main/java/com/kset/common/monitor/config/KsetMonitorProperties.java)。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`enabled`](../../kset-starter-monitor/src/main/java/com/kset/common/monitor/config/KsetMonitorProperties.java) | `boolean` | `true` | — |
| [`traceEnabled`](../../kset-starter-monitor/src/main/java/com/kset/common/monitor/config/KsetMonitorProperties.java) | `boolean` | `true` | — |
| [`traceHeader`](../../kset-starter-monitor/src/main/java/com/kset/common/monitor/config/KsetMonitorProperties.java) | `String` | — | 网关透传的 trace 头名；未设置时回退兼容键 kset.cloud.gateway.trace-header |

### `KsetMonitorProperties.Mybatis`

类型：class；[源码](../../kset-starter-monitor/src/main/java/com/kset/common/monitor/config/KsetMonitorProperties.java)。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`enabled`](../../kset-starter-monitor/src/main/java/com/kset/common/monitor/config/KsetMonitorProperties.java) | `boolean` | `true` | — |

### `KsetMonitorProperties.HttpClient`

类型：class；[源码](../../kset-starter-monitor/src/main/java/com/kset/common/monitor/config/KsetMonitorProperties.java)。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`enabled`](../../kset-starter-monitor/src/main/java/com/kset/common/monitor/config/KsetMonitorProperties.java) | `boolean` | `true` | — |

### `KsetMonitorProperties.Redis`

类型：class；[源码](../../kset-starter-monitor/src/main/java/com/kset/common/monitor/config/KsetMonitorProperties.java)。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`enabled`](../../kset-starter-monitor/src/main/java/com/kset/common/monitor/config/KsetMonitorProperties.java) | `boolean` | `true` | — |

### `KsetMonitorProperties.ThreadPool`

类型：class；[源码](../../kset-starter-monitor/src/main/java/com/kset/common/monitor/config/KsetMonitorProperties.java)。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`enabled`](../../kset-starter-monitor/src/main/java/com/kset/common/monitor/config/KsetMonitorProperties.java) | `boolean` | `true` | — |
| [`tracePropagationEnabled`](../../kset-starter-monitor/src/main/java/com/kset/common/monitor/config/KsetMonitorProperties.java) | `boolean` | `true` | — |

### `KsetMonitorProperties.Async`

类型：class；[源码](../../kset-starter-monitor/src/main/java/com/kset/common/monitor/config/KsetMonitorProperties.java)。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`enabled`](../../kset-starter-monitor/src/main/java/com/kset/common/monitor/config/KsetMonitorProperties.java) | `boolean` | `true` | — |

### `KsetMonitorProperties.Scheduled`

类型：class；[源码](../../kset-starter-monitor/src/main/java/com/kset/common/monitor/config/KsetMonitorProperties.java)。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`enabled`](../../kset-starter-monitor/src/main/java/com/kset/common/monitor/config/KsetMonitorProperties.java) | `boolean` | `true` | — |

### `KsetMonitorProperties.SamplerConfig`

类型：class；[源码](../../kset-starter-monitor/src/main/java/com/kset/common/monitor/config/KsetMonitorProperties.java)。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`rate`](../../kset-starter-monitor/src/main/java/com/kset/common/monitor/config/KsetMonitorProperties.java) | `double` | `1.0` | — |

### `KsetMonitorProperties.Reporter`

类型：class；[源码](../../kset-starter-monitor/src/main/java/com/kset/common/monitor/config/KsetMonitorProperties.java)。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`asyncEnabled`](../../kset-starter-monitor/src/main/java/com/kset/common/monitor/config/KsetMonitorProperties.java) | `boolean` | `false` | — |
| [`queueCapacity`](../../kset-starter-monitor/src/main/java/com/kset/common/monitor/config/KsetMonitorProperties.java) | `int` | `2048` | — |

## kset-starter-redis

### `KsetRedisProperties`

类型：class；配置前缀：`kset.redis`；[源码](../../kset-starter-redis/src/main/java/com/kset/redis/config/KsetRedisProperties.java)。

职责：Redis 约定配置（本 starter 自持；原寄放于 kset-cloud，1.0.15 起下沉）。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`enabled`](../../kset-starter-redis/src/main/java/com/kset/redis/config/KsetRedisProperties.java) | `boolean` | `true` | — |
| [`keyPrefix`](../../kset-starter-redis/src/main/java/com/kset/redis/config/KsetRedisProperties.java) | `String` | `""` | — |
| [`defaultTtl`](../../kset-starter-redis/src/main/java/com/kset/redis/config/KsetRedisProperties.java) | `Duration` | `Duration.ofMinutes(30)` | 全局默认过期时间，禁止永久 key；写操作未传 ttl 时使用 |
| [`maxTtl`](../../kset-starter-redis/src/main/java/com/kset/redis/config/KsetRedisProperties.java) | `Duration` | — | 可选：单次写入允许的最大 TTL |
| [`cache`](../../kset-starter-redis/src/main/java/com/kset/redis/config/KsetRedisProperties.java) | `Cache` | `new Cache()` | — |
| [`stream`](../../kset-starter-redis/src/main/java/com/kset/redis/config/KsetRedisProperties.java) | `Stream` | `new Stream()` | — |
| [`redisson`](../../kset-starter-redis/src/main/java/com/kset/redis/config/KsetRedisProperties.java) | `Redisson` | `new Redisson()` | — |
| [`lock`](../../kset-starter-redis/src/main/java/com/kset/redis/config/KsetRedisProperties.java) | `Lock` | `new Lock()` | — |
| [`sources`](../../kset-starter-redis/src/main/java/com/kset/redis/config/KsetRedisProperties.java) | `Map<String, RedisSourceProperties>` | `new LinkedHashMap<>()` | — |

### `KsetRedisProperties.Lock`

类型：class；[源码](../../kset-starter-redis/src/main/java/com/kset/redis/config/KsetRedisProperties.java)。

职责：分布式锁（注解锁等）。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`annotationEnabled`](../../kset-starter-redis/src/main/java/com/kset/redis/config/KsetRedisProperties.java) | `boolean` | `true` | 是否注册 @KsetLocked AOP 切面 |
| [`validateTargets`](../../kset-starter-redis/src/main/java/com/kset/redis/config/KsetRedisProperties.java) | `boolean` | `true` | 启动时校验注解锁目标并输出失效场景提示 |

### `KsetRedisProperties.Stream`

类型：class；[源码](../../kset-starter-redis/src/main/java/com/kset/redis/config/KsetRedisProperties.java)。

职责：高危操作流式/分批参数。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`scanBatchSize`](../../kset-starter-redis/src/main/java/com/kset/redis/config/KsetRedisProperties.java) | `int` | `500` | — |
| [`deleteBatchSize`](../../kset-starter-redis/src/main/java/com/kset/redis/config/KsetRedisProperties.java) | `int` | `500` | — |
| [`mgetChunkSize`](../../kset-starter-redis/src/main/java/com/kset/redis/config/KsetRedisProperties.java) | `int` | `100` | — |
| [`hashScanCount`](../../kset-starter-redis/src/main/java/com/kset/redis/config/KsetRedisProperties.java) | `int` | `100` | — |
| [`useUnlink`](../../kset-starter-redis/src/main/java/com/kset/redis/config/KsetRedisProperties.java) | `boolean` | `true` | — |

### `KsetRedisProperties.Redisson`

类型：class；[源码](../../kset-starter-redis/src/main/java/com/kset/redis/config/KsetRedisProperties.java)。

职责：Redisson 分布式锁与通用编解码（Jackson）。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`enabled`](../../kset-starter-redis/src/main/java/com/kset/redis/config/KsetRedisProperties.java) | `boolean` | `true` | — |
| [`lockWaitTime`](../../kset-starter-redis/src/main/java/com/kset/redis/config/KsetRedisProperties.java) | `Duration` | `Duration.ofSeconds(3)` | — |
| [`lockLeaseTime`](../../kset-starter-redis/src/main/java/com/kset/redis/config/KsetRedisProperties.java) | `Duration` | `Duration.ofSeconds(30)` | — |
| [`threads`](../../kset-starter-redis/src/main/java/com/kset/redis/config/KsetRedisProperties.java) | `int` | `16` | — |
| [`nettyThreads`](../../kset-starter-redis/src/main/java/com/kset/redis/config/KsetRedisProperties.java) | `int` | `32` | — |

### `KsetRedisProperties.Cache`

类型：class；[源码](../../kset-starter-redis/src/main/java/com/kset/redis/config/KsetRedisProperties.java)。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`enabled`](../../kset-starter-redis/src/main/java/com/kset/redis/config/KsetRedisProperties.java) | `boolean` | `false` | — |
| [`defaultTtl`](../../kset-starter-redis/src/main/java/com/kset/redis/config/KsetRedisProperties.java) | `Duration` | `Duration.ofHours(1)` | — |

### `KsetRedisProperties.RedisSourceProperties`

类型：class；[源码](../../kset-starter-redis/src/main/java/com/kset/redis/config/KsetRedisProperties.java)。

职责：命名 Redis 数据源（kset.redis.sources.{name}）。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`enabled`](../../kset-starter-redis/src/main/java/com/kset/redis/config/KsetRedisProperties.java) | `boolean` | `true` | — |
| [`keyPrefix`](../../kset-starter-redis/src/main/java/com/kset/redis/config/KsetRedisProperties.java) | `String` | `""` | — |
| [`host`](../../kset-starter-redis/src/main/java/com/kset/redis/config/KsetRedisProperties.java) | `String` | `"localhost"` | — |
| [`port`](../../kset-starter-redis/src/main/java/com/kset/redis/config/KsetRedisProperties.java) | `int` | `6379` | — |
| [`database`](../../kset-starter-redis/src/main/java/com/kset/redis/config/KsetRedisProperties.java) | `int` | `0` | — |
| [`password`](../../kset-starter-redis/src/main/java/com/kset/redis/config/KsetRedisProperties.java) | `String` | — | — |
| [`ssl`](../../kset-starter-redis/src/main/java/com/kset/redis/config/KsetRedisProperties.java) | `boolean` | `false` | — |
| [`timeout`](../../kset-starter-redis/src/main/java/com/kset/redis/config/KsetRedisProperties.java) | `Duration` | `Duration.ofSeconds(2)` | — |
| [`pool`](../../kset-starter-redis/src/main/java/com/kset/redis/config/KsetRedisProperties.java) | `RedisSourcePoolProperties` | `new RedisSourcePoolProperties()` | — |
| [`cluster`](../../kset-starter-redis/src/main/java/com/kset/redis/config/KsetRedisProperties.java) | `Cluster` | `new Cluster()` | — |

### `KsetRedisProperties.RedisSourcePoolProperties`

类型：class；[源码](../../kset-starter-redis/src/main/java/com/kset/redis/config/KsetRedisProperties.java)。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`enabled`](../../kset-starter-redis/src/main/java/com/kset/redis/config/KsetRedisProperties.java) | `boolean` | `false` | — |
| [`maxActive`](../../kset-starter-redis/src/main/java/com/kset/redis/config/KsetRedisProperties.java) | `int` | `8` | — |
| [`maxIdle`](../../kset-starter-redis/src/main/java/com/kset/redis/config/KsetRedisProperties.java) | `int` | `8` | — |
| [`minIdle`](../../kset-starter-redis/src/main/java/com/kset/redis/config/KsetRedisProperties.java) | `int` | `0` | — |
| [`maxWait`](../../kset-starter-redis/src/main/java/com/kset/redis/config/KsetRedisProperties.java) | `Duration` | `Duration.ofMillis(-1)` | — |

### `KsetRedisProperties.Cluster`

类型：class；[源码](../../kset-starter-redis/src/main/java/com/kset/redis/config/KsetRedisProperties.java)。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`enabled`](../../kset-starter-redis/src/main/java/com/kset/redis/config/KsetRedisProperties.java) | `boolean` | `false` | — |
| [`nodes`](../../kset-starter-redis/src/main/java/com/kset/redis/config/KsetRedisProperties.java) | `List<String>` | `List.of()` | — |
| [`maxRedirects`](../../kset-starter-redis/src/main/java/com/kset/redis/config/KsetRedisProperties.java) | `int` | `3` | — |

### `KsetRedisLockOptions`

类型：class；[源码](../../kset-starter-redis/src/main/java/com/kset/redis/lock/KsetRedisLockOptions.java)。

职责：抢锁参数。 <p>Watchdog 只能通过 #rejectNowWatchdog()、#waitThenFailWatchdog(Duration)、 #blockUntilWatchdog() 或 @KsetLocked(lease = "0s") 打开。 #rejectNow(Duration) 传入 Duration#ZERO 会在执行时按 TTL 策略拒绝。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`waitTime`](../../kset-starter-redis/src/main/java/com/kset/redis/lock/KsetRedisLockOptions.java) | `Duration` | — | — |
| [`leaseTime`](../../kset-starter-redis/src/main/java/com/kset/redis/lock/KsetRedisLockOptions.java) | `Duration` | — | — |
| [`strategy`](../../kset-starter-redis/src/main/java/com/kset/redis/lock/KsetRedisLockOptions.java) | `KsetRedisLockStrategy` | — | — |
| [`watchdog`](../../kset-starter-redis/src/main/java/com/kset/redis/lock/KsetRedisLockOptions.java) | `boolean` | — | — |

### `KsetRedisGroupRankChangeEvent`

类型：record；[源码](../../kset-starter-redis/src/main/java/com/kset/redis/rank/group/KsetRedisGroupRankChangeEvent.java)。

职责：分组榜排名变化事件。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`boardId`](../../kset-starter-redis/src/main/java/com/kset/redis/rank/group/KsetRedisGroupRankChangeEvent.java) | `String` | — | — |
| [`changeType`](../../kset-starter-redis/src/main/java/com/kset/redis/rank/group/KsetRedisGroupRankChangeEvent.java) | `KsetRedisGroupRankChangeType` | — | — |
| [`groupId`](../../kset-starter-redis/src/main/java/com/kset/redis/rank/group/KsetRedisGroupRankChangeEvent.java) | `String` | — | — |
| [`memberId`](../../kset-starter-redis/src/main/java/com/kset/redis/rank/group/KsetRedisGroupRankChangeEvent.java) | `String` | — | — |
| [`previousScore`](../../kset-starter-redis/src/main/java/com/kset/redis/rank/group/KsetRedisGroupRankChangeEvent.java) | `Double` | — | — |
| [`newScore`](../../kset-starter-redis/src/main/java/com/kset/redis/rank/group/KsetRedisGroupRankChangeEvent.java) | `double` | — | — |
| [`previousRank`](../../kset-starter-redis/src/main/java/com/kset/redis/rank/group/KsetRedisGroupRankChangeEvent.java) | `Integer` | — | — |
| [`newRank`](../../kset-starter-redis/src/main/java/com/kset/redis/rank/group/KsetRedisGroupRankChangeEvent.java) | `int` | — | — |
| [`rankChanged`](../../kset-starter-redis/src/main/java/com/kset/redis/rank/group/KsetRedisGroupRankChangeEvent.java) | `boolean` | — | — |

### `KsetRedisRankChangeEvent`

类型：record；[源码](../../kset-starter-redis/src/main/java/com/kset/redis/rank/KsetRedisRankChangeEvent.java)。

职责：成员排名或分值变化事件（在 KsetRedisRankBoard#increment 等写操作后触发）。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`boardId`](../../kset-starter-redis/src/main/java/com/kset/redis/rank/KsetRedisRankChangeEvent.java) | `String` | — | — |
| [`member`](../../kset-starter-redis/src/main/java/com/kset/redis/rank/KsetRedisRankChangeEvent.java) | `String` | — | — |
| [`previousScore`](../../kset-starter-redis/src/main/java/com/kset/redis/rank/KsetRedisRankChangeEvent.java) | `Double` | — | — |
| [`newScore`](../../kset-starter-redis/src/main/java/com/kset/redis/rank/KsetRedisRankChangeEvent.java) | `double` | — | — |
| [`previousRank`](../../kset-starter-redis/src/main/java/com/kset/redis/rank/KsetRedisRankChangeEvent.java) | `Integer` | — | — |
| [`newRank`](../../kset-starter-redis/src/main/java/com/kset/redis/rank/KsetRedisRankChangeEvent.java) | `int` | — | — |
| [`rankChanged`](../../kset-starter-redis/src/main/java/com/kset/redis/rank/KsetRedisRankChangeEvent.java) | `boolean` | — | — |

### `KsetRedisRankEntry`

类型：record；[源码](../../kset-starter-redis/src/main/java/com/kset/redis/rank/KsetRedisRankEntry.java)。

职责：榜单中的一条排名记录（名次从 1 开始）。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`rank`](../../kset-starter-redis/src/main/java/com/kset/redis/rank/KsetRedisRankEntry.java) | `int` | — | — |
| [`member`](../../kset-starter-redis/src/main/java/com/kset/redis/rank/KsetRedisRankEntry.java) | `String` | — | — |
| [`score`](../../kset-starter-redis/src/main/java/com/kset/redis/rank/KsetRedisRankEntry.java) | `double` | — | — |

### `KsetRedisRankOptions`

类型：class；[源码](../../kset-starter-redis/src/main/java/com/kset/redis/rank/KsetRedisRankOptions.java)。

职责：单榜/分组榜的代码侧配置（不依赖 YAML）。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`boardId`](../../kset-starter-redis/src/main/java/com/kset/redis/rank/KsetRedisRankOptions.java) | `String` | — | — |
| [`redisKey`](../../kset-starter-redis/src/main/java/com/kset/redis/rank/KsetRedisRankOptions.java) | `String` | — | — |
| [`order`](../../kset-starter-redis/src/main/java/com/kset/redis/rank/KsetRedisRankOptions.java) | `KsetRedisRankOrder` | — | — |
| [`ttl`](../../kset-starter-redis/src/main/java/com/kset/redis/rank/KsetRedisRankOptions.java) | `Duration` | — | — |

## kset-starter-schedule

### `KsetScheduleProperties`

类型：class；配置前缀：`kset.scheduler`；[源码](../../kset-starter-schedule/src/main/java/com/kset/schedule/config/KsetScheduleProperties.java)。

职责：kset 定时任务配置。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`lock`](../../kset-starter-schedule/src/main/java/com/kset/schedule/config/KsetScheduleProperties.java) | `Lock` | `new Lock()` | — |

### `KsetScheduleProperties.Lock`

类型：class；[源码](../../kset-starter-schedule/src/main/java/com/kset/schedule/config/KsetScheduleProperties.java)。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`enabled`](../../kset-starter-schedule/src/main/java/com/kset/schedule/config/KsetScheduleProperties.java) | `boolean` | `true` | 是否启用唯一运行锁。 |
| [`tableName`](../../kset-starter-schedule/src/main/java/com/kset/schedule/config/KsetScheduleProperties.java) | `String` | `"t_kset_schedule_lock"` | 锁表名。 |
| [`atMostFor`](../../kset-starter-schedule/src/main/java/com/kset/schedule/config/KsetScheduleProperties.java) | `Duration` | `Duration.ofMinutes(10)` | 宕机兜底释放时间全局默认值（com.kset.schedule.annotation.KsetTaskLock#atMostFor() 为空时生效）。 |
| [`autoCreateTable`](../../kset-starter-schedule/src/main/java/com/kset/schedule/config/KsetScheduleProperties.java) | `boolean` | `true` | 启动时自动建锁表（CREATE TABLE IF NOT EXISTS）。 |

## kset-starter-web

### `KsetWebProperties`

类型：class；配置前缀：`kset.web`；[源码](../../kset-starter-web/src/main/java/com/kset/web/config/KsetWebProperties.java)。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`oplog`](../../kset-starter-web/src/main/java/com/kset/web/config/KsetWebProperties.java) | `Oplog` | `new Oplog()` | — |
| [`requestLogging`](../../kset-starter-web/src/main/java/com/kset/web/config/KsetWebProperties.java) | `RequestLogging` | `new RequestLogging()` | — |
| [`response`](../../kset-starter-web/src/main/java/com/kset/web/config/KsetWebProperties.java) | `Response` | `new Response()` | — |
| [`exceptionHandling`](../../kset-starter-web/src/main/java/com/kset/web/config/KsetWebProperties.java) | `ExceptionHandling` | `new ExceptionHandling()` | — |

### `KsetWebProperties.Oplog`

类型：class；[源码](../../kset-starter-web/src/main/java/com/kset/web/config/KsetWebProperties.java)。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`enabled`](../../kset-starter-web/src/main/java/com/kset/web/config/KsetWebProperties.java) | `boolean` | `true` | — |
| [`userIdHeader`](../../kset-starter-web/src/main/java/com/kset/web/config/KsetWebProperties.java) | `String` | `"X-User-Id"` | — |

### `KsetWebProperties.RequestLogging`

类型：class；[源码](../../kset-starter-web/src/main/java/com/kset/web/config/KsetWebProperties.java)。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`enabled`](../../kset-starter-web/src/main/java/com/kset/web/config/KsetWebProperties.java) | `boolean` | `false` | — |

### `KsetWebProperties.Response`

类型：class；[源码](../../kset-starter-web/src/main/java/com/kset/web/config/KsetWebProperties.java)。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`traceIdEnabled`](../../kset-starter-web/src/main/java/com/kset/web/config/KsetWebProperties.java) | `boolean` | `true` | — |

### `KsetWebProperties.ExceptionHandling`

类型：class；[源码](../../kset-starter-web/src/main/java/com/kset/web/config/KsetWebProperties.java)。

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`useHttpStatus`](../../kset-starter-web/src/main/java/com/kset/web/config/KsetWebProperties.java) | `boolean` | `false` | Whether framework exception responses should use real HTTP error status. Default false keeps HTTP 200 and returns error status through ApiResponse.code. |
| [`defaultBusinessCode`](../../kset-starter-web/src/main/java/com/kset/web/config/KsetWebProperties.java) | `int` | `400` | Business code used when BusinessException does not carry a numeric errorCode. |
| [`validationCode`](../../kset-starter-web/src/main/java/com/kset/web/config/KsetWebProperties.java) | `int` | `400` | Business code used for validation errors. |
| [`systemCode`](../../kset-starter-web/src/main/java/com/kset/web/config/KsetWebProperties.java) | `int` | `500` | Business code used for unknown system errors. |
| [`methodNotAllowedCode`](../../kset-starter-web/src/main/java/com/kset/web/config/KsetWebProperties.java) | `int` | `405` | Business code used when request method is not supported. |
| [`notFoundCode`](../../kset-starter-web/src/main/java/com/kset/web/config/KsetWebProperties.java) | `int` | `404` | Business code used when requested resource is not found. |
| [`unsupportedMediaTypeCode`](../../kset-starter-web/src/main/java/com/kset/web/config/KsetWebProperties.java) | `int` | `415` | Business code used when request media type is not supported. |
| [`badRequestCode`](../../kset-starter-web/src/main/java/com/kset/web/config/KsetWebProperties.java) | `int` | `400` | Business code used when request body cannot be read. |
| [`parseBusinessErrorCode`](../../kset-starter-web/src/main/java/com/kset/web/config/KsetWebProperties.java) | `boolean` | `true` | Whether numeric BusinessException#errorCode should be parsed as ApiResponse code. |

### `ApiResponse`

类型：class；[源码](../../kset-starter-web/src/main/java/com/kset/web/response/ApiResponse.java)。

职责：统一 API 响应体

| 字段 | Java 类型 | 声明初始化值 | 源码说明 |
| --- | --- | --- | --- |
| [`code`](../../kset-starter-web/src/main/java/com/kset/web/response/ApiResponse.java) | `int` | — | — |
| [`message`](../../kset-starter-web/src/main/java/com/kset/web/response/ApiResponse.java) | `String` | — | — |
| [`data`](../../kset-starter-web/src/main/java/com/kset/web/response/ApiResponse.java) | `T` | — | — |
| [`traceId`](../../kset-starter-web/src/main/java/com/kset/web/response/ApiResponse.java) | `String` | — | — |
