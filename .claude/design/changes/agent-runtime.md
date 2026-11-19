# Agent Runtime 模块变更记录

- feature-key：`agent-runtime`
- 当前 change-id：`agent-harness-20261119-02`
- 当前状态：`removed`
- 当前入口：无；旧 Runtime 源码和 Maven 模块已移除，现行公共入口见 `.claude/design/changes/agent-harness.md`

> 2026-11-19 退役快照：用户授权移除旧 Agent；`kset-agent-runtime/` 已从当前源码删除。基线 Git ref `20c4554ee53bc21c43c2cb5223084beb49b2b630` 可定位旧源码；新 Harness 不兼容旧 Java API。以下历史条目保留其原时点状态。

## 实现索引

| change-id | 状态 | 入口 | 替代目标 | 回滚来源 | 回滚可用性 | 清理条件 |
| --- | --- | --- | --- | --- | --- | --- |
| `agent-runtime-20261102-01` | active-compatible | `kset-agent-runtime/src/main/java/com/kset/agent/runtime/AgentRuntime.java#run/resume` | N/A | N/A | 不可用 | 现有注册入口仍承接调用方 |
| `agent-runtime-20261102-02` | active-compatible | `kset-agent-runtime/src/main/java/com/kset/agent/runtime/AgentRuntime.java#Builder` | `agent-runtime-20261102-01` 的注册写法 | Git ref `8ac1c1b6ba2c4d45ba3ec38055a48cf806414c3f` | 可用 | 便捷入口仍承接调用方 |
| `agent-runtime-20261105-01` | active | `kset-agent-runtime/src/main/java/com/kset/agent/runtime/AgentRuntime.java#kernel` | 无默认耗时日志的 Runtime 组装 | Git ref `4e4fbb5dce95c2600d5fe7be997bce0577da039f` | 可用 | 耗时日志稳定且原有 Listener 继续可用 |

## agent-runtime-20261105-01

- 日期：2026-11-05
- 目标：复用 Core 生命周期耗时，在 Runtime 默认输出 Agent 调用与关键步骤的结构化日志；模型达到 30 秒或工具动作达到 10 秒时将该步骤日志提升为 WARNING。
- 判定时间：2026-11-05 +08:00
- 上线状态：不涉及兼容
- 判定方式：N/A
- 判定证据：仅增加观测日志，不修改 run/resume 参数、结果或 Core 协议。
- 证据来源：`kset-agent-runtime/src/main/java/com/kset/agent/runtime/AgentRuntime.java`、`kset-agent-core/src/main/java/com/kset/agent/core/event/AgentLifecycleContext.java`
- 当时代码状态：`AgentRuntime#kernel` 只登记宿主 Listener；Core 已提供每步及每次调用的 elapsed；现有 Listener 接口与调用方保持可用。
- 兼容选择：N/A
- 回滚目标：`agent-runtime-20261102-02`
- 旧版本正式线上主分支及对比基线 Git SHA：待确认
- 旧版正式发布版本：待确认
- 基线 Git ref：`4e4fbb5dce95c2600d5fe7be997bce0577da039f`
- 结果 Git ref：`ca58bea`
- 回滚来源：Git ref `4e4fbb5dce95c2600d5fe7be997bce0577da039f`
- 回滚可用性：可用

| 文件 | 符号 | 职责 | 动作 | 状态 |
| --- | --- | --- | --- | --- |
| `kset-agent-core/src/main/java/com/kset/agent/core/event/AgentTimingLogListener.java` | `AgentTimingLogListener` | 输出调用、模型、动作、轮次和检查点耗时，并标记超阈值步骤 | add | active |
| `kset-agent-runtime/src/main/java/com/kset/agent/runtime/AgentRuntime.java` | `AgentRuntime#kernel` | 默认登记无状态耗时 Listener | modify | active |
| `kset-agent-runtime/README.md` | 生命周期日志 | 说明日志字段、级别与敏感内容边界 | modify | active |
| `kset-agent-core/docs/protocol-extension-guide.md` | 生命周期观测 | 说明 Core 直接注册入口 | modify | active |
| `.claude/skills/kset-framework-coder/references/project-spec.md` | Agent Runtime 模块约定 | 记录默认耗时日志约定 | modify | active |

### 回滚步骤

1. 从 Runtime 组装中移除默认耗时 Listener，并撤销该类与对应说明。
2. 保留原有宿主 Listener 注册入口和 Core 生命周期观测契约。
3. 更新本记录中的当前 change-id 与实现状态。

### 静态自查

- 模型步骤达到 30 秒、`tool_call` 或 `tool_batch` 动作达到 10 秒时，同一条步骤日志提升为 WARNING 并标记 `slow=true`；失败事件仍按原有 WARNING 输出。
- 只使用生命周期事件中的耗时与稳定标识，不保存按调用可变状态，不读取模型正文、工具参数或 Observation。
- 已核对工具动作类型、模型重试耗时与日志等级；按项目规则未编译、运行测试或启动服务。

## agent-runtime-20261102-02

- 日期：2026-11-02
- 目标：简化普通工具、Skill、事件与模型指标的宿主注册写法，保留原有注册入口。
- 判定时间：2026-11-02 +08:00
- 上线状态：不涉及兼容
- 判定方式：N/A
- 判定证据：仅新增 Builder 重载，原有方法签名和执行链路保持可用。
- 证据来源：`kset-agent-runtime/src/main/java/com/kset/agent/runtime/AgentRuntime.java`、`kset-agent-runtime/README.md`
- 当时代码状态：`Builder.tools(source/registry)`、`skill(AgentSkill)`、`mcpTools(client)`、`listener(AgentLifecycleListener)` 为现有入口；本轮不改变 Core 或 MCP 客户端契约，现有调用方无需迁移。
- 兼容选择：N/A
- 回滚目标：`agent-runtime-20261102-01`
- 基线 Git ref：`8ac1c1b6ba2c4d45ba3ec38055a48cf806414c3f`
- 结果 Git ref：N/A
- 回滚来源：Git ref `8ac1c1b6ba2c4d45ba3ec38055a48cf806414c3f`
- 回滚可用性：可用

| 文件 | 符号 | 职责 | 动作 | 状态 |
| --- | --- | --- | --- | --- |
| `kset-agent-runtime/src/main/java/com/kset/agent/runtime/AgentRuntime.java` | `Builder#tool/skill/onEvent/onModelMetrics` | 便捷注册并复用现有组装链路 | add | active |
| `kset-agent-runtime/src/test/java/com/kset/agent/runtime/AgentRuntimeRegistrationTest.java` | `AgentRuntimeRegistrationTest` | 静态工具、动态 Skill 与观测注册契约 | add | active |
| `kset-agent-runtime/README.md` | 组装与调用 | 展示简化后的宿主调用 | modify | active |
| `kset-agent-runtime/docs/runtime-object-contract.md` | Builder 入口字段表 | 记录新增注册入口 | modify | active |
| `.claude/skills/kset-framework-coder/references/project-spec.md` | Agent Runtime 模块约定 | 记录便捷入口的职责边界 | modify | active |

### 回滚步骤

1. 仅撤销本轮新增的 Builder 便捷入口与对应示例和测试。
2. 保留 `agent-runtime-20261102-01` 的原有注册方法及调用方。
3. 更新本记录的实现状态和回滚目标。

## agent-runtime-20261102-01

- 日期：2026-11-02
- 目标：新增独立组装层、业务上下文透传、Skill 工具集合与 MCP 工具适配。
- 判定时间：2026-11-02 +08:00
- 上线状态：不涉及兼容
- 判定方式：N/A
- 判定证据：全新模块，无旧入口。
- 证据来源：`pom.xml`、`kset-agent-runtime/pom.xml`
- 当时代码状态：Core 直接由宿主组装；Runtime 无旧契约、无调用方迁移。
- 兼容选择：N/A
- 回滚目标：N/A
- 基线 Git ref：`9e293cbbbfc60e4d2a077841c6a41f0f951e3e28`
- 结果 Git ref：`8ac1c1b6ba2c4d45ba3ec38055a48cf806414c3f`
- 回滚来源：N/A
- 回滚可用性：不可用

| 文件 | 符号 | 职责 | 动作 | 状态 |
| --- | --- | --- | --- | --- |
| `kset-agent-runtime/src/main/java/com/kset/agent/runtime/AgentRuntime.java` | `AgentRuntime#run/resume` | 每次调用组装 Core | add | active |
| `kset-agent-runtime/src/main/java/com/kset/agent/runtime/AgentBusinessContext.java` | `AgentBusinessContext` | 业务身份与数据快照 | add | active |
| `kset-agent-runtime/src/main/java/com/kset/agent/runtime/AgentToolSource.java` | `AgentToolSource` | 按次生成可见工具 | add | active |
| `kset-agent-runtime/src/main/java/com/kset/agent/runtime/AgentSkill.java` | `AgentSkill` | 可复用工具集合 | add | active |
| `kset-agent-runtime/src/main/java/com/kset/agent/runtime/mcp/McpAgentToolSource.java` | `McpAgentToolSource` | MCP 工具目录适配 | add | active |

### 回滚步骤

1. 新模块尚无替代目标；如需撤销，先确认业务方未引入构件。
2. 由用户授权后移除模块注册、依赖管理与文件，并更新本记录。
