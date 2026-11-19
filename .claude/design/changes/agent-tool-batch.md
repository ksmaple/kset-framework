# Agent 工具批次变更记录

- feature-key：`agent-tool-batch`
- 当前 change-id：`agent-tool-batch-20261109-03`
- 当前状态：`active`
- 当前入口：`kset-agent-core/src/main/java/com/kset/agent/core/action/StandardActionHandlers.java#ToolBatchHandler.handle`、`ToolFlowActionHandler#handle`

## 实现索引

| change-id | 状态 | 入口 | 替代目标 | 回滚来源 | 回滚可用性 | 清理条件 |
| --- | --- | --- | --- | --- | --- | --- |
| `agent-tool-batch-20261106-01` | active | `AgentKernelBuilder#build`、`AgentRuntime#kernel` | 默认同步工具执行器 | Git ref `ca58bea599e46378dc557ed27d8d59f940da5658` | 可用 | N/A |
| `agent-tool-batch-20261106-02` | active-compatible | `AgentKernelBuilder#build`、`ToolFlowActionHandler#handle` | 无，新增只读依赖工具扩展 | Git ref `ca58bea599e46378dc557ed27d8d59f940da5658`；前一轮本地未提交改动需保留 | 受限，前一轮结果无独立 Git ref | 依赖工具扩展仍承接调用 |
| `agent-tool-batch-20261109-03` | active | `StandardActionHandlers#ToolCallHandler`、`ToolBatchHandler`、`ToolFlowActionHandler#handle` | 同步单工具和不可中断的批次等待 | Git `b9df9ca80fe00240e66841aa07b26273713e9c05` | 可用 | 取消链路验证后保留 |

## agent-tool-batch-20261109-03

- 日期与判定时间：2026-11-09 Asia/Shanghai。
- 目标：主循环短周期检查取消和 deadline，取消底层工具 Future；写操作可能已执行时保留结果未知核验状态。
- 上线状态：开发未上线；本地 `1.0.17-SNAPSHOT` 无对应正式发布证据。
- 当时代码状态：单工具在主循环线程直接运行；批量和只读依赖流使用长时间阻塞等待，取消 Future 时没有中断运行线程；默认工具池满时由调用线程代跑。
- 兼容选择：标准动作、观察和核验状态契约不变；自定义工具执行器仍须提供真正异步的有界执行。
- 相关文件与符号：`kset-agent-core/src/main/java/com/kset/agent/core/action/CancellableActionWait.java#await`、`StandardActionHandlers#ToolCallHandler`、`StandardActionHandlers#ToolBatchHandler`、`ToolFlowActionHandler#handle`、`kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentKernelBuilder.java#createDefaultToolExecutor`。
- 回滚目标：恢复本变更前的动作等待和默认执行器；保留其他 `1.0.17-SNAPSHOT` 改动。
- 基线 Git ref：`b9df9ca80fe00240e66841aa07b26273713e9c05`。
- 结果 Git ref：working-tree（未提交）。
- 回滚来源：基线 Git ref 中上述文件的对应实现。
- 回滚可用性：可用。
- 验证：源码静态核对及 `git diff --check`；按项目规则未编译或运行测试。
- 限制：`Future.cancel(true)` 请求中断，不保证无法响应中断的外部工具立即停止；写工具结果不明时必须按原操作身份核验，不可盲目重试。

## agent-tool-batch-20261106-02

- 日期：2026-11-06
- 目标：可选协议支持只读工具结果引用和依赖分层并行；失败、超时或引用缺失时阻断下游。
- 判定时间：2026-11-06 +08:00
- 上线状态：开发未上线
- 判定方式：沿用本地 1.0.17-SNAPSHOT 开发快照；该轮扩展在本次任务前不存在。
- 当时代码状态：标准 `tool_batch` 并行执行独立调用；扩展 Handler 无内置预执行 pending 快照与结果核验，不能安全承接写入副作用。
- 兼容选择：新增独立可选协议及 Handler，保留 `agent-json:v1` 和标准 `tool_batch` 契约。
- 基线 Git ref：`ca58bea599e46378dc557ed27d8d59f940da5658`
- 结果 Git ref：N/A
- 回滚来源：基线 Git ref 与前一轮本地未提交改动。
- 回滚可用性：受限；须保留前一轮本地改动并仅移除本轮扩展，待结果提交后补充可定位 ref。
- 本轮相关文件：`AgentKernelBuilder`、`AgentRuntime`、新协议 Codec、扩展 Handler、Core/Runtime 用例与相关文档。
- 回滚目标：保留上一轮标准 `tool_batch` 并发实现，移除本轮可选扩展注册及新增文件。

| 文件 | 符号 | 职责 | 动作 | 状态 |
| --- | --- | --- | --- | --- |
| `kset-agent-core/src/main/java/com/kset/agent/core/action/ToolFlowPlan.java` | `ToolFlowPlan#parse` | 校验调用、引用及依赖图 | add | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/action/ToolFlowActionHandler.java` | `ToolFlowActionHandler#handle` | 只读预检、分层并行及失败阻断 | add | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/protocol/json/AgentJsonFlowV1Codec.java` | `AgentJsonFlowV1Codec#decode` | 独立协议与扩展动作映射 | add | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentKernelBuilder.java` | `readOnlyToolFlow` | 按需注册协议和 Handler | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/event/AgentTimingLogListener.java` | `isSlow` | 依赖流程沿用工具慢日志阈值 | modify | active |
| `kset-agent-runtime/src/main/java/com/kset/agent/runtime/AgentRuntime.java` | `readOnlyToolFlow` | 按次工具集合启用扩展 | modify | active |
| `kset-agent-core/src/test/java/com/kset/agent/core/loop/AgentToolFlowTest.java` | 依赖工具用例 | 并行、引用、阻断、预检及非法图覆盖 | add | active |
| `kset-agent-runtime/src/test/java/com/kset/agent/runtime/AgentRuntimeFlowTest.java` | Runtime 扩展用例 | 验证按次工具集合装配 | modify | active |
| `kset-agent-core/docs/protocols/agent-json-flow-v1.md` | 协议契约 | 说明字段、边界和失败语义 | add | active |
| `kset-agent-core/README.md`、`kset-agent-runtime/README.md`、`kset-agent-core/docs/protocol-extension-guide.md` | 使用说明 | 更新可选扩展入口 | modify | active |
| `.claude/skills/kset-framework-coder/references/project-spec.md` | Agent 扩展约定 | 记录只读依赖批次范围 | modify | active |

### 本轮静态自查

- `tool_flow` 使用独立协议；标准 `agent-json:v1` 和 `tool_batch` 不变。
- 只读及无需确认检查在任何调用提交前完成；有副作用工具不进入扩展。依赖失败、缺失引用、取消和超时不会启动后续依赖层。
- 执行数量受现有批次上限约束，调用和批次 deadline 沿用现有选项；工具结果按输入顺序返回。
- 工具输出仍取自各自权威数据源，跨缓存或副本的可见性须由适配器保证；本轮不新增数据库、Redis 或外部基础设施。
- 用例源码已覆盖 Core/Runtime 主要路径；按项目规则未编译、运行测试或启动服务。

## agent-tool-batch-20261106-01

- 日期：2026-11-06
- 目标：标准 `tool_batch` 默认并行调用独立工具，仍允许宿主显式覆盖执行器。
- 判定时间：2026-11-06 +08:00
- 上线状态：开发未上线
- 判定方式：自动判定
- 判定证据：Agent Core 与 Runtime 仍为 `1.0.17-SNAPSHOT`，无 `v1.0.17` 标签；已发布的 `v1.0.16` 不含这两个模块。
- 证据来源：`kset-agent-core/pom.xml`、`kset-agent-runtime/pom.xml`、Git 标签、Git ref `ca58bea599e46378dc557ed27d8d59f940da5658`
- 当时代码状态：Core 与 Runtime 默认 `Runnable::run`；`tool_batch` 已逐项提交 `CompletableFuture`，只有宿主配置并行 Executor 时才并发。现有 `toolExecutor` 覆盖入口保留。
- 兼容选择：开发阶段按用户要求调整默认行为；显式执行器配置不变。
- 回滚目标：N/A
- 旧版本正式线上主分支及对比基线 Git SHA：待确认
- 旧版正式发布版本：待确认
- 基线 Git ref：`ca58bea599e46378dc557ed27d8d59f940da5658`
- 结果 Git ref：N/A
- 回滚来源：Git ref `ca58bea599e46378dc557ed27d8d59f940da5658`
- 回滚可用性：可用

| 文件 | 符号 | 职责 | 动作 | 状态 |
| --- | --- | --- | --- | --- |
| `kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentKernelBuilder.java` | 默认工具执行器 | 提供有界内部并行执行器 | modify | active |
| `kset-agent-runtime/src/main/java/com/kset/agent/runtime/AgentRuntime.java` | `AgentRuntime#kernel` | 未显式配置时沿用 Core 默认执行器 | modify | active |
| `kset-agent-core/src/test/java/com/kset/agent/core/loop/AgentLoopKernelConcurrencyTest.java` | 工具批次并发用例 | 验证 Core 默认并发 | modify | active |
| `kset-agent-runtime/src/test/java/com/kset/agent/runtime/AgentRuntimeFlowTest.java` | 工具批次并发用例 | 验证 Runtime 默认并发 | modify | active |
| `kset-agent-core/README.md` | 工具并行说明 | 更新默认执行行为和资源边界 | modify | active |
| `kset-agent-runtime/README.md` | 工具并行说明 | 说明 Runtime 默认行为 | modify | active |
| `kset-agent-core/docs/protocols/agent-json-v1.md` | `tool_batch` 协议说明 | 更新默认并发与超时说明 | modify | active |
| `.claude/skills/kset-framework-coder/references/project-spec.md` | Agent Core/Runtime 约定 | 记录默认执行器与覆盖口径 | modify | active |

### 回滚步骤

1. 按基线 Git ref 恢复两个 Builder 的默认同步执行器，并恢复对应文档约定。
2. 保留标准 `tool_batch` 协议、Handler 和显式 `toolExecutor` 入口。

### 静态自查

- Core 默认有界线程池延迟创建线程，最多 8 个守护线程和 64 个排队任务；队列饱和时调用线程执行，不创建无界线程或队列。
- Runtime 仅在显式配置时覆盖 Core 执行器；单工具调用、批次大小、结果顺序、超时与未知结果核验出口未改变。
- Core 与 Runtime 的默认并发用例已更新；已执行 `mvn -B -pl kset-agent-runtime -am -DskipTests "-Dmaven.test.skip=true" clean install`，父工程、Core 与 Runtime 构建成功，未编译或运行测试，未启动服务。
