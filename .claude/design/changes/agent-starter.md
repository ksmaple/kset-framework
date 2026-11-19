# Agent 核心模块变更记录

- feature-key：`agent-core`
- 当前 change-id：`agent-harness-20261119-02`
- 当前状态：`removed`
- 当前入口：无；旧 Core 源码和 Maven 模块已移除，现行公共入口见 `.claude/design/changes/agent-harness.md`

> 2026-11-19 退役快照：用户授权移除旧 Agent；`kset-agent-core/` 已从当前源码删除。基线 Git ref `20c4554ee53bc21c43c2cb5223084beb49b2b630` 可定位旧源码；新 Harness 不兼容旧 Java API 与快照。以下历史条目保留其原时点状态。

> 2026-11-03 清理说明：历史条目中的 `AgentRunState#restoreForRollback/applyForRollback/stoppedForRollback` 已移除，恢复旧实现改用对应 Git ref；详见 `rollback-code-cleanup.md`。历史时点记录保留原样。

## 实现索引

| change-id | 状态 | 入口 | 替代目标 | 回滚来源 | 回滚可用性 | 清理条件 |
| --- | --- | --- | --- | --- | --- | --- |
| `agent-starter-20260903-01` | rollback-candidate | `com.kset.agent.core.AgentReActExecutor#execute` | N/A | Git ref `a7a63f2` | 可用 | 新内核稳定且无旧 API 调用方 |
| `agent-core-20261008-01` | rollback-candidate | `com.kset.agent.core.loop.AgentLoopKernel#run` | `agent-starter-20260903-01` | Git ref `db91ee5` | 可用 | 新执行上下文与停止契约稳定 |
| `agent-core-20261009-02` | rollback-candidate | `com.kset.agent.core.loop.AgentLoopKernel#run` | `agent-core-20261008-01` | Git ref `db91ee5` | 可用 | 核心错误契约稳定 |
| `agent-core-20261009-03` | rollback-candidate | `com.kset.agent.core.loop.AgentLoopKernel#run` | `agent-core-20261009-02` | N/A | 不可用 | 协议固定与输入边界完成静态核验 |
| `agent-core-20261009-04` | rollback-candidate | `com.kset.agent.core.loop.AgentLoopKernel#run` | `agent-core-20261009-03` | Git ref `f6bad26` | 可用 | 本轮状态与扩展边界完成静态核验 |
| `agent-core-20261009-05` | rollback-candidate | `com.kset.agent.core.loop.AgentLoopKernel#run` | `agent-core-20261009-04` | Git ref `f6bad26` | 可用 | 统一观测契约完成静态核验 |
| `agent-core-20261009-06` | rollback-candidate | `com.kset.agent.core.loop.AgentLoopKernel#run` | `agent-core-20261009-05` | Git ref `b6fdba0` | 可用 | 并发隔离与幂等契约完成静态核验 |
| `agent-core-20261009-07` | rollback-candidate | `com.kset.agent.core.loop.AgentLoopKernel#run` | `agent-core-20261009-06` | Git ref `b6fdba0` | 可用 | 工具主动报告未知结果的停止契约完成静态核验 |
| `agent-core-20261009-08` | rollback-candidate | `com.kset.agent.core.loop.AgentLoopKernel#run` | `agent-core-20261009-07` | Git ref `b6fdba0` + 未提交工作区 | 可用 | 确定性核验恢复与递归载荷隔离完成静态核验 |
| `agent-core-20261009-09` | rollback-candidate | `com.kset.agent.core.loop.AgentLoopKernel#run/resume` | `agent-core-20261009-08` | Git ref `b6fdba0` + 未提交工作区 | 可用 | 核心流程与并发模拟用例通过 |
| `agent-core-20261009-10` | rollback-candidate | `com.kset.agent.core.loop.AgentLoopKernel#run/resume` | `agent-core-20261009-09` | Git ref `b6fdba0` + 未提交工作区 | 可用 | 核心缺陷修复回归通过 |
| `agent-core-20261009-11` | rollback-candidate | `com.kset.agent.core.loop.AgentLoopKernel#run/resume` | `agent-core-20261009-10` | Git ref `b6fdba0` + 未提交工作区 | 可用 | 步骤观测与全局 ID 契约完成静态核验 |
| `agent-core-20261010-12` | rollback-candidate | `com.kset.agent.core.loop.AgentLoopKernel#run/resume` | `agent-core-20261009-11` | Git ref `b6fdba0` + 未提交工作区 | 可用 | 命名与专用对象说明完成静态核验 |
| `agent-core-20261010-13` | rollback-candidate | `com.kset.agent.core.loop.AgentLoopKernel#run/resume` | `agent-core-20261010-12` | Git ref `b6fdba0` + 未提交工作区 | 可用 | 内核公共边界收敛完成静态核验 |
| `agent-core-20261010-14` | rollback-candidate | `com.kset.agent.core.loop.AgentLoopKernel#run/resume` | `agent-core-20261010-13` | Git ref `c686867` + 未提交工作区 | 可用 | 模型重试契约稳定 |
| `agent-core-20261010-15` | rollback-candidate | `com.kset.agent.core.loop.AgentLoopKernel#run/resume` | `agent-core-20261010-14` | `AgentLoopKernel#generateModel` 单次调用入口 | 可用 | 命名文档与内核接口映射明确 |
| `agent-core-20261010-16` | rollback-candidate | `kset-agent-core/README.md#核心对象命名` | `agent-core-20261010-15` | 当前未提交工作区 | 可用 | 字段语义收敛完成静态核验 |
| `agent-core-20261010-17` | rollback-candidate | `com.kset.agent.core.loop.AgentLoopKernel#run/resume` | `agent-core-20261010-16` | Git ref `d028b66` | 可用 | 确认恢复与计划校验修复完成静态核验 |
| `agent-core-20261010-18` | rollback-candidate | `com.kset.agent.core.loop.AgentLoopKernel#run/resume` | `agent-core-20261010-17` | Git ref `d028b66` + 本轮前未提交工作区 | 可用 | 命名契约收敛完成静态核验 |
| `agent-core-20261010-19` | rollback-candidate | `com.kset.agent.core.loop.AgentLoopKernel#run/resume` | `agent-core-20261010-18` | Git ref `2a8af7c` | 可用 | v4 快照与核验恢复契约稳定后评估 |
| `agent-core-20261010-20` | rollback-candidate | `com.kset.agent.core.loop.AgentLoopKernel#run/resume` | `agent-core-20261010-19` | Git ref `2a8af7c` | 可用 | 新模型输入边界完成静态核验 |
| `agent-core-20261023-21` | rollback-candidate | `com.kset.agent.core.loop.AgentLoopKernel#run/resume` | `agent-core-20261010-20` | Git ref `96cf32e` | 可用 | 字段命名契约完成静态核验 |
| `agent-core-20261023-22` | rollback-candidate | `com.kset.agent.core.loop.AgentLoopKernel#run/resume` | `agent-core-20261023-21` | Git ref `cd925c9` | 可用 | 本轮字段契约稳定后评估 |
| `agent-core-20261024-23` | rollback-candidate | `com.kset.agent.core.loop.AgentLoopKernel#run/resume` | `agent-core-20261023-22` | Git ref `8ac1c1b` | 可用 | 本轮快照优化稳定后评估 |
| `agent-core-20261102-24` | rollback-candidate | `com.kset.agent.core.loop.AgentLoopKernel#run/resume` | `agent-core-20261024-23` | Git ref `8ac1c1b` | 可用 | 提示构造修复后评估 |
| `agent-core-20261104-25` | rollback-candidate | `com.kset.agent.core.strategy.ReactReasoningStrategy#nextTurn/validate` | `agent-core-20261102-24` | Git ref `5bc753897dc4c63f4c126d623cd07f9e46fbc88e` + 本轮前未提交工作区 | 可用 | 精简提示后评估 |
| `agent-core-20261104-26` | rollback-candidate | `com.kset.agent.core.strategy.ReactReasoningStrategy#nextTurn` | `agent-core-20261104-25` | Git ref `5bc753897dc4c63f4c126d623cd07f9e46fbc88e` + 本轮前未提交工作区 | 可用 | token 容量自动压缩稳定后评估 |
| `agent-core-20261104-27` | rollback-candidate | `com.kset.agent.core.loop.AgentLoopKernel#run/resume` | `agent-core-20261104-26` | Git ref `5bc753897dc4c63f4c126d623cd07f9e46fbc88e` + 本轮前未提交工作区 | 可用 | 内置模型容量稳定后评估 |
| `agent-core-20261104-28` | rollback-candidate | `com.kset.agent.core.loop.AgentLoopKernel#run/resume` | `agent-core-20261104-27` | Git ref `5bc753897dc4c63f4c126d623cd07f9e46fbc88e` + 本轮前未提交工作区 | 可用 | 旧版容量目录稳定后评估 |
| `agent-core-20261104-29` | rollback-candidate | `com.kset.agent.core.model.BuiltInModels#list/find` | `agent-core-20261104-28` | Git ref `5bc753897dc4c63f4c126d623cd07f9e46fbc88e` + 本轮前未提交工作区 | 可用 | 本轮模型名绑定与输出语义稳定后评估 |
| `agent-core-20261104-30` | rollback-candidate | `com.kset.agent.core.loop.AgentLoopKernel#run/resume` | `agent-core-20261104-29` | Git ref `5bc753897dc4c63f4c126d623cd07f9e46fbc88e` + 本轮前未提交工作区 | 可用 | 自定义容量扩展稳定后评估 |
| `agent-core-20261104-31` | rollback-candidate | `com.kset.agent.core.loop.AgentLoopKernel#run/resume` | `agent-core-20261104-30` | Git ref `5bc753897dc4c63f4c126d623cd07f9e46fbc88e` + 本轮前未提交工作区 | 可用 | 动态模型容量收紧稳定后评估 |
| `agent-core-20261104-32` | active | `com.kset.agent.core.loop.AgentLoopKernel#run/resume` | `agent-core-20261104-31` | Git ref `5bc753897dc4c63f4c126d623cd07f9e46fbc88e` + 本轮前未提交工作区 | 可用 | N/A |

## agent-starter-20260903-01

- 日期：2026-09-03
- 目标：从 kset-rag 下沉 ReAct 编排、工作流持久化及业务适配端口。
- 判定时间：2026-09-03 +08:00
- 上线状态：开发未上线
- 判定方式：自动判定
- 判定证据：新模块首次创建，无发布记录、无业务调用方，kset-rag 未引入该构件。
- 证据来源：Git 历史、`kset-agent-core/README.md`
- 当时代码状态：入口为巨型 ReAct 执行器；接口包含项目、文档、代码仓库、权限、指标及工作流持久化；无已知调用方。
- 兼容选择：N/A
- 回滚目标：N/A
- 基线 Git ref：N/A
- 结果 Git ref：`a7a63f2`
- 回滚来源：Git ref `a7a63f2`
- 回滚可用性：可用

## agent-core-20261008-01

- 日期：2026-10-08
- 目标：重建为框架无关的稳定 Agent 循环内核，支持推理策略、版本化协议、动作、停止策略、检查点及生命周期扩展。
- 判定时间：2026-10-08 +08:00
- 上线状态：开发未上线
- 判定方式：自动判定
- 判定证据：模块位于 `1.0.17-SNAPSHOT`，创建提交晚于 `v1.0.16`，无包含该模块的发布标签；模块 README 明示 kset-rag 尚未接入。
- 证据来源：Git tag/history、`kset-agent-core/README.md`、`CHANGELOG.md`
- 当时代码状态：入口为 `AgentReActExecutor#execute`；协议固定在执行器内；停止条件分散；已知调用方为空；旧实现状态为 active-compatible。
- 兼容选择：直接替换（用户于 2026-10-08 明确授权删除现有非核心 API）
- 回滚目标：`agent-starter-20260903-01`
- 基线 Git ref：`a7a63f2`
- 结果 Git ref：`db91ee5`
- 回滚来源：Git ref `a7a63f2`
- 回滚可用性：可用

| 文件 | 符号 | 职责 | 动作 | 状态 |
| --- | --- | --- | --- | --- |
| `kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentLoopKernel.java` | `AgentLoopKernel#run` | 策略无关的循环驱动 | add | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/protocol` | `AgentProtocolCodec`、`AgentProtocolRegistry` | 版本化协议扩展 | add | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/strategy` | `AgentReasoningStrategy`、`ReactReasoningStrategy` | 推理策略扩展与默认 ReAct | add | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/stop` | `AgentStopController`、`AgentStopPolicy` | 集中式停止判断 | add | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/action` | `AgentActionHandler`、`AgentActionRegistry` | 动作分发扩展 | add | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/AgentReActExecutor.java` | `AgentReActExecutor` | 旧巨型执行器 | remove | removed |
| `kset-agent-core/src/main/java/com/kset/agent/core/engine` | `StateGraphWorkflowEngine` | 旧持久化工作流引擎 | remove | removed |
| `kset-agent-core/src/main/java/com/kset/agent/core/autoconfigure` | 全部符号 | 旧 Spring 自动装配 | remove | removed |
| `kset-agent-core/src/main/java/com/kset/agent/core/port` | 全部符号 | 旧指标和查询端口 | remove | removed |
| `kset-agent-core/src/main/java/com/kset/agent/core/spi` | 旧业务 SPI | 项目、文档、代码仓库和权限适配 | remove | removed |

### 回滚步骤

1. 从 Git ref `a7a63f2` 恢复 `kset-agent-core` 模块内容。
2. 恢复 Spring 自动装配入口及旧 SPI、工作流入口关系。
3. 将 `agent-starter-20260903-01` 标为 active，并将本轮实现标为 rollback-candidate。

## agent-core-20261009-02

- 日期：2026-10-09
- 目标：补齐单次 Agent 任务的执行上下文、活动执行超时、取消传播、工具幂等上下文、容量限制和宿主加载快照边界。
- 判定时间：2026-10-09 +08:00
- 上线状态：开发未上线
- 判定方式：自动判定
- 判定证据：模块仍为 `1.0.17-SNAPSHOT`，HEAD 不属于任何发布标签，仓内无模块调用方，kset-rag 尚未引入该构件。
- 证据来源：Git ref `db91ee5`、`kset-agent-core/pom.xml`、`kset-agent-core/README.md`
- 当时代码状态：入口为 `AgentLoopKernel#run/resume`；模型与工具调用不接收执行 deadline 和取消上下文；恢复沿用任务创建时间计算超时；CheckpointPort 同时承担快照查询；已知调用方为空，迁移尚未开始，现实现状态为 active。
- 兼容选择：直接替换（用户于 2026-10-09 确认本轮完整优化计划）
- 回滚目标：`agent-core-20261008-01`
- 基线 Git ref：`db91ee5`
- 结果 Git ref：`f6bad26`
- 回滚来源：Git ref `db91ee5`
- 回滚可用性：可用

| 文件 | 符号 | 职责 | 动作 | 状态 |
| --- | --- | --- | --- | --- |
| `kset-agent-core/src/main/java/com/kset/agent/core/execution/AgentExecutionContext.java` | `AgentExecutionContext` | 单次活动执行的 deadline 与取消上下文 | add | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/tool/AgentToolContext.java` | `AgentToolContext` | 工具调用标识与执行上下文 | add | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/model/AgentModel.java` | `AgentModel#generate` | deadline-aware 模型调用 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/tool/AgentTool.java` | `AgentTool#execute` | 幂等、取消与超时上下文 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/action/StandardActionHandlers.java` | 标准工具动作处理器 | 传递工具上下文并限制批量等待 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/action/ToolCallAction.java` | `ToolCallAction` | 固定必填幂等调用标识 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentLoopKernel.java` | `run`、`resume`、`execute` | 单次任务循环与活动执行计时 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/checkpoint/AgentCheckpointPort.java` | `save` | 只写时携带不可变请求上下文 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/checkpoint/AgentCheckpointPort.java` | `find` | 移除核心主动加载快照职责 | remove | removed |
| `kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentLoopKernel.java` | `resume(AgentRequest)` | 移除核心主动加载快照入口 | remove | removed |
| `kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentLoopOptions.java` | 容量与工具超时选项 | 固定每轮动作和工具批次边界 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/stop` | `AgentStopContext`、`AgentStopController`、`AgentStopReason` | 使用活动执行 deadline 并暴露容量停止原因 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/protocol/json/AgentJsonV1Codec.java` | `AgentJsonV1Codec` | 要求工具协议提供非空 callId | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentKernelBuilder.java` | `build` | 启动期冻结工具集合 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/tool/FixedAgentToolRegistry.java` | `FixedAgentToolRegistry` | 构建时冻结工具集合 | add | active |
| `kset-agent-core/README.md` | 接入与运行契约 | 记录稳定边界和线程安全要求 | modify | active |
| `.claude/skills/kset-framework-coder/references/project-spec.md` | Agent 核心模块约定 | 固定单次任务边界与宿主职责 | modify | active |
| `CHANGELOG.md` | v1.0.17-SNAPSHOT | 记录稳定执行上下文与边界变化 | modify | active |

### 回滚步骤

1. 从 Git ref `db91ee5` 恢复 `kset-agent-core` 模块内容及文档入口。
2. 恢复 `AgentModel`、`AgentTool`、`AgentCheckpointPort` 和 `AgentLoopKernel#resume(AgentRequest)` 旧契约。
3. 将 `agent-core-20261008-01` 标为 active，并将本轮实现标为 rollback-candidate。

### 静态自查

- 请求幂等：标准工具协议强制提供 `callId`，并通过 `AgentToolContext` 传给工具；持久化幂等结果由宿主工具适配器负责。
- 任务抢占：不适用；核心只执行宿主已经取得执行权的单次任务，会话单飞和跨节点租约归宿主。
- 数据权威源：宿主持久化快照是恢复权威源；核心不再主动查询快照。
- 一致性：CheckpointPort 同步输出不可变请求与快照，宿主从请求属性取得 owner/fencing 凭据并负责 revision 和条件写；写入失败停止后续推进。
- 重试与恢复：协议纠错受错误次数限制；恢复必须显式传入同 runId、task 和 protocol 的版本化快照，每次恢复重新计算活动执行 deadline。
- 容量与取消：每轮动作、工具批次、单工具时限和批次时限均有明确边界；Model、Tool 通过执行上下文接收 deadline 和 cancellation。

## agent-core-20261009-03

- 日期：2026-10-09
- 目标：收敛为只保证同步单次循环正确性的核心，并为公开失败点提供稳定错误枚举、异常和失败结果。
- 判定时间：2026-10-09 +08:00
- 上线状态：开发未上线
- 判定方式：自动判定
- 判定证据：模块仍为 `1.0.17-SNAPSHOT`，仓内无模块调用方，kset-rag 尚未接入。
- 证据来源：Git ref `db91ee5`、`kset-agent-core/README.md`、本记录 `agent-core-20261009-02`
- 当时代码状态：入口为 `AgentLoopKernel#run/resume`；停止原因已有枚举，但调用方输入、扩展契约和运行异常仍主要依赖字符串消息；协议 metadata 与内核属性共用命名空间；已知调用方为空。
- 兼容选择：直接替换（用户于 2026-10-09 确认本轮错误契约与循环正确性优化计划）
- 回滚目标：`agent-core-20261009-02`
- 基线 Git ref：`db91ee5` + 未提交的 `agent-core-20261009-02` 工作区
- 结果 Git ref：N/A
- 回滚来源：N/A
- 回滚可用性：不可用

| 文件 | 符号 | 职责 | 动作 | 状态 |
| --- | --- | --- | --- | --- |
| `kset-agent-core/src/main/java/com/kset/agent/core/AgentErrorCode.java` | `AgentErrorCode` | 内核公开错误枚举 | add | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/AgentCoreException.java` | `AgentCoreException` | 携带稳定错误码的内核异常 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/api/AgentFailure.java` | `AgentFailure` | 运行期技术失败结果 | add | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/api/AgentResult.java` | `AgentResult` | 暴露可判定失败信息 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/api/AgentRequest.java` | 构造校验 | 请求错误统一映射 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/action/AgentActionResult.java` | `AgentActionResult` | 限定动作正常终态契约 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/action/AgentActionRegistry.java` | `dispatch` | 未注册动作错误映射 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/action/AgentObservation.java` | `failure` | 接收内核错误枚举 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/action/StandardActionHandlers.java` | 标准工具动作处理器 | 工具失败分类与批次拒绝处理 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentLoopKernel.java` | `run`、`resume`、`execute` | 统一扩展边界、异常映射和动作后停止判断 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentKernelBuilder.java` | 组装入口 | 配置错误统一映射 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentLoopOptions.java` | 容量与超时配置 | 配置错误统一映射 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentRunSnapshot.java` | 构造校验 | 快照恢复边界校验 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentRunState.java` | `decisionAccepted` | 隔离协议 metadata | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/stop/AgentStopController.java` | `evaluateAfterAction` | 动作间停止与无进展控制 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/stop/AgentStopPolicy.java` | 扩展契约 | 策略停止原因规范化为 POLICY | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/protocol/AgentProtocolException.java` | `protocolCode` | 区分核心错误码与协议扩展码 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/protocol/AgentProtocolRegistry.java` | `require` | 未注册协议错误映射 | modify | active |
| `kset-agent-core/README.md` | 错误、超时和恢复契约 | 明确核心保证与非目标 | modify | active |
| `.claude/skills/kset-framework-coder/references/project-spec.md` | Agent 核心模块约定 | 固定同步循环和错误契约 | modify | active |
| `CHANGELOG.md` | v1.0.17-SNAPSHOT | 记录公开错误契约 | modify | active |

### 回滚步骤

1. 本轮未提交前根据工作区差异恢复 `agent-core-20261009-02` 的接口与循环逻辑。
2. 恢复字符串异常及协议 metadata 直接合并行为。
3. 将 `agent-core-20261009-02` 标为 active，并将本轮实现标为 rollback-candidate。

### 静态自查

- 请求幂等：核心只透传稳定 `callId`；工具幂等结果持久化与未知结果确认由宿主负责。
- 任务抢占：不适用；会话单飞、任务排队、owner/fencing 和跨节点租约不进入同步循环内核。
- 数据权威源：宿主提供的版本化快照与工具权威结果是恢复依据；核心不主动查询持久化状态。
- 一致性：循环固定动作应用、检查点写入、动作后停止判断的顺序；检查点失败转为结构化技术失败，外部副作用与快照不承诺原子提交。
- 重试与恢复：协议错误按上限纠正；`resume` 只恢复逻辑状态，工具调用重试必须复用 `callId` 并由宿主保证幂等。
- 错误契约：停止原因、前置异常、协议异常、运行技术失败和工具 Observation 已分层，公开分支使用枚举而非消息文本。
- 验证方式：仅执行源码搜索、差异与空白检查；按用户要求未运行测试、未编译。

## agent-core-20261009-04

- 日期：2026-10-09
- 目标：固定内置协议与组装入口，修复确认、请求属性、JSON 严格解析和工具 deadline 边界，并补齐协议扩展及 kset-rag 映射文档。
- 判定时间：2026-10-09 +08:00
- 上线状态：开发未上线
- 判定方式：自动判定
- 判定证据：模块仍为 `1.0.17-SNAPSHOT`，仓内无模块调用方，kset-rag 尚未引入该构件。
- 证据来源：Git ref `db91ee5`、`kset-agent-core/pom.xml`、本记录 `agent-core-20261009-03`
- 当时代码状态：入口为 `AgentLoopKernel#run/resume`；Builder 默认注册内置协议，但 public 构造器可绕过；请求属性可占用内核键；内置协议与 kset-rag `code-v7` wire 字段不兼容；迁移尚未开始。
- 兼容选择：直接替换（用户于 2026-10-09 确认本轮协议加固与接入文档计划）
- 回滚目标：`agent-core-20261009-03`
- 基线 Git ref：`db91ee5` + 未提交的 `agent-core-20261009-03` 工作区
- 结果 Git ref：`f6bad26`
- 回滚来源：Git ref `f6bad26`
- 回滚可用性：可用

| 文件 | 符号 | 职责 | 动作 | 状态 |
| --- | --- | --- | --- | --- |
| `kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentLoopKernel.java` | 构造器、`run/resume`、`supportedProtocols` | 限制内核只由 Builder 组装，协议注册在副作用前校验，并公开只读协议清单 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/api/AgentRequest.java` | 构造校验、`isActionConfirmed` | 隔离保留属性并修正确认判断 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/action/AgentActionContext.java` | 执行上下文 | 将 Kernel 权威 Clock 传给动作处理器 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentKernelBuilder.java` | `objectMapper`、`build` | 固定严格内置 JSON Codec | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/protocol/json/AgentJsonV1Codec.java` | 构造器、协议错误码 | 防御性复制并固定严格解析 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/protocol/json/AgentJsonV1ErrorCode.java` | `AgentJsonV1ErrorCode` | 内置协议固定错误枚举 | add | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/action/ConfirmationAction.java` | 构造校验 | 要求稳定确认 ID | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/action/StandardActionHandlers.java` | 工具执行 deadline | 使用实际执行时刻检查剩余时限 | modify | active |
| `kset-agent-core/docs/protocols/agent-json-v1.md` | 内置 wire 协议 | 固定六动作、字段和错误语义 | add | active |
| `kset-agent-core/docs/protocol-extension-guide.md` | 协议 SPI 指南 | 说明选择、注册、版本与扩展动作 | add | active |
| `kset-agent-core/docs/integration/kset-rag.md` | kset-rag 映射 | 固定跨仓适配边界与字段映射 | add | active |
| `kset-agent-core/README.md` | 协议文档入口 | 增加跳转及选择示例 | modify | active |
| `CHANGELOG.md` | v1.0.17-SNAPSHOT | 记录协议加固与文档 | modify | active |

### 回滚步骤

1. 根据本轮工作区差异恢复 `agent-core-20261009-03` 的构造、请求、协议和工具执行边界。
2. 移除本轮新增协议文档入口与映射说明。
3. 将 `agent-core-20261009-03` 标为 active，并将本轮实现标为 rollback-candidate。

### 静态自查

- 请求幂等：内置协议继续强制非空 `callId`；修复属性缺失时字符串 `"null"` 被误判为已确认的问题；RAG 映射文档明确兼容生成的 ID 必须先持久化再用于确认、重试和恢复。
- 任务抢占：不适用；会话单飞、跨节点租约和任务排队继续归宿主，Kernel 仅暴露单次同步 `run/resume`。
- 数据权威源：宿主快照和工具权威结果不变；请求执行凭据不再复制进内核状态，CheckpointPort 仍同时接收请求和快照。
- 一致性：Kernel 构造器收窄为包内，只允许 Builder 固定注册内置协议；重复协议 ID 构建失败，未注册协议在 Listener、Checkpoint 和 Model 等副作用前拒绝，同一运行及恢复禁止切换协议。
- 协议安全：内置 Codec 防御性复制 ObjectMapper，关闭宽松 JSON 特性，拒绝重复、未知和 null 字段；公开固定 `AgentJsonV1ErrorCode`，自定义协议继续使用独立扩展码。
- 超时与并发：动作处理器使用 Kernel Clock 获取实际开始时间；批次绝对 deadline 同时限制排队工具，Kernel 仍可并发处理独立会话且共享扩展必须线程安全。
- RAG 映射：文档区分模型传输、Agent 控制和 SSE 输出三层协议；`code-v7` 使用独立协议 ID，带依赖批次通过扩展 Action/Handler 接入，不修改循环内核。
- 验证方式：执行源码搜索、文档链接检查、协议错误枚举交叉检查和 `git diff --check`；按用户要求未运行测试、未编译。

## agent-core-20261009-05

- 日期：2026-10-09
- 目标：补齐确认消费与临时状态清理，封闭 Handler/Strategy 保留属性写入边界，消除协议 ID 拼接碰撞，并明确终态 Listener 与协议异常消息契约。
- 判定时间：2026-10-09 +08:00
- 上线状态：开发未上线
- 判定方式：自动判定
- 判定证据：模块仍为 `1.0.17-SNAPSHOT`，基线提交未包含于发布标签，仓内及 kset-rag 均无模块调用方。
- 证据来源：Git ref `f6bad26`、`kset-agent-core/pom.xml`、本记录 `agent-core-20261009-04`
- 当时代码状态：入口为 `AgentLoopKernel#run/resume`；确认动作总是暂停；动作状态只支持合并不支持删除；自定义 Handler/Strategy 可改写保留属性；协议注册使用拼接字符串键；终态 Listener 实际吞异常但文档未固定该语义。
- 兼容选择：直接替换（用户于 2026-10-09 确认修复）
- 回滚目标：`agent-core-20261009-04`
- 基线 Git ref：`f6bad26`
- 结果 Git ref：N/A
- 回滚来源：Git ref `f6bad26`
- 回滚可用性：可用

| 文件 | 符号 | 职责 | 动作 | 状态 |
| --- | --- | --- | --- | --- |
| `kset-agent-core/src/main/java/com/kset/agent/core/action/AgentActionResult.java` | 动作状态变更契约 | 显式表达属性删除 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/action/StandardActionHandlers.java` | 标准动作处理器 | 消费确认并清理待处理状态 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentRunState.java` | `apply` | 删除属性后再合并动作状态 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentLoopKernel.java` | 扩展结果校验 | 保护 `agent.*` 与 Strategy 命名空间 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentKernelBuilder.java` | `build` | 固定并校验 Strategy ID | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/strategy/ReactReasoningStrategy.java` | Prompt 与状态输出 | 只暴露公开确认 ID 并限定 `react.*` 状态 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/protocol/AgentProtocolRegistry.java` | 协议注册键 | 使用结构化协议 ID 并冻结注册身份 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/protocol/AgentProtocolId.java` | `key` | 明确字符串仅用于展示 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/protocol/AgentProtocolException.java` | 协议错误消息 | 规范化空消息 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/event/AgentLifecycleListener.java` | 终态回调契约 | 明确 best-effort 语义 | modify | active |
| `kset-agent-core/README.md` | 扩展和恢复契约 | 记录命名空间、确认和 Listener 语义 | modify | active |
| `kset-agent-core/docs` | 协议与接入文档 | 同步确认消费和扩展边界 | modify | active |
| `CHANGELOG.md` | v1.0.17-SNAPSHOT | 记录内核边界修复 | modify | active |

### 回滚步骤

1. 从 Git ref `f6bad26` 恢复本轮涉及的核心源码和文档。
2. 恢复动作结果只合并属性、字符串协议键及原扩展边界。
3. 将 `agent-core-20261009-04` 标为 active，并将本轮实现标为 rollback-candidate。

### 静态自查

- 请求幂等：工具继续以稳定 `callId` 作为幂等键；确认 ID 在恢复请求中显式传入，核心不生成或替换业务幂等键。
- 任务抢占：不适用；本轮不改变单次同步调用边界，会话单飞、owner/fencing 和跨节点租约仍由宿主保证。
- 数据权威源：宿主快照与工具权威结果不变；`agent.pendingAction` 仅是内核临时状态，不作为外部副作用权威结果。
- 一致性：动作属性先删除后合并；确认消费、工具结束和最终回答清理 pending；Handler/Strategy 越界状态变更统一转为 `EXTENSION_CONTRACT_VIOLATION`。
- 重试与恢复：恢复继续复用快照协议和原 callId；已确认 ID 可幂等重复传入，协议空消息被规范化后再写入状态。
- 协议身份：Registry 使用结构化 `AgentProtocolId`，Codec ID 构建期只读取一次，字符串 `key()` 不再参与唯一性判断。
- 终态通知：`onError/onStop` 固定为 best-effort，不改变已确定结果；可靠持久化仍通过 CheckpointPort。
- 验证方式：仅执行源码调用点搜索、文档链接存在性、POM XML 解析和 `git diff --check`；按用户要求未运行测试、未编译。

## agent-core-20261009-06

- 日期：2026-10-09
- 目标：补齐运行、轮次、模型、决策、动作、检查点和终态的固定外部观测端点，统一调用身份、事件顺序、时间与耗时上下文，并保证已经返回的模型和动作结果先被观测及应用再执行停止判定。
- 判定时间：2026-10-09 +08:00
- 上线状态：开发未上线
- 判定方式：自动判定
- 判定证据：模块仍为 `1.0.17-SNAPSHOT`，基线提交未包含于发布标签，仓内及 kset-rag 均无模块调用方。
- 证据来源：Git ref `f6bad26`、`kset-agent-core/pom.xml`、本记录 `agent-core-20261009-05`
- 当时代码状态：生命周期 Listener 只有部分后置回调，没有固定事件枚举、run/resume 调用身份、阶段耗时、模型/动作开始、轮次完成、检查点前后及最终结果回调；core 不内置日志、指标或链路依赖。
- 兼容选择：直接替换（用户于 2026-10-09 明确要求全部提供对外端点并开始优化）
- 回滚目标：`agent-core-20261009-05`
- 基线 Git ref：`f6bad26` + 未提交的 `agent-core-20261009-05` 工作区
- 结果 Git ref：N/A
- 回滚来源：Git ref `f6bad26` + 未提交工作区
- 回滚可用性：可用

| 文件 | 符号 | 职责 | 动作 | 状态 |
| --- | --- | --- | --- | --- |
| `kset-agent-core/src/main/java/com/kset/agent/core/event` | 调用类型、事件类型与生命周期上下文 | 固定外部观测协议 | add | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/event/AgentLifecycleListener.java` | 全生命周期回调 | 暴露模型、动作、轮次、检查点与结果前后端点 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/model/ModelCallMetrics.java` | 模型调用指标 | 固定模型、供应商、Token 与请求身份 | add | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/model/ModelResponse.java` | 模型响应 | 携带标准调用指标并保留旧构造入口 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentLoopKernel.java` | 生命周期事件顺序 | 已返回结果先观测/应用，停止检查随后执行 | modify | active |
| `kset-agent-core/README.md` | 外部观测契约 | 记录可靠性与日志安全边界 | modify | active |
| `kset-agent-core/docs` | 扩展与 kset-rag 接入 | 记录各观测端点映射 | modify | active |
| `.claude/skills/kset-framework-coder/references/project-spec.md` | Agent 核心模块约定 | 固定观测协议边界 | modify | active |
| `CHANGELOG.md` | v1.0.17-SNAPSHOT | 记录统一观测端点 | modify | active |

### 回滚步骤

1. 根据本轮工作区差异移除新增事件上下文与模型指标类型，恢复原 Listener 方法集合。
2. 恢复 `agent-core-20261009-05` 的模型、动作、检查点与终态通知顺序。
3. 将 `agent-core-20261009-05` 标为 active，并将本轮实现标为 rollback-candidate。

### 静态自查

- 请求幂等：工具副作用继续使用稳定 `callId`；观测事件使用 `invocationId + sequence` 作为单次调用内唯一身份，不替代业务幂等键。
- 任务抢占：不适用；生命周期调用类型只区分 `RUN/RESUME`，会话单飞和 owner/fencing 仍由宿主原子条件写保证。
- 数据权威源：`AgentResult` 是同步返回权威结果，成功写入的 Checkpoint 是恢复权威状态；Listener 事件只作投影和遥测。
- 一致性：模型返回后先发出完成事件；动作返回后先应用结果并发出完成事件，再执行取消/超时停止判定；检查点前后事件严格包围实际保存调用。
- 重试与恢复：每次 resume 生成新的 invocationId，runId 保持不变；外部可区分恢复调用并按 eventId 去重同次上报。
- 观测协议：固定事件枚举、版本化上下文、严格递增序号、零基 actionIndex、实际扩展调用耗时、整轮耗时、模型调用指标和最终结果回调均已公开；旧 Listener 默认方法继续由新回调委托。
- 上报故障：非关键和终态 Listener 失败不改变运行结果，其他 Listener 可通过 `onListenerError` 观测；关键活动回调仍转换为 `LISTENER_FAILED`。
- 日志安全：core 不引入日志、指标或 Trace 依赖；文档明确禁止记录未脱敏 Prompt、模型原文、工具参数、Observation 和请求属性。
- 验证方式：执行源码调用点交叉检查、括号平衡检查、文档映射检查和 `git diff --check`；按用户要求未运行测试、未编译。

## agent-core-20261009-07

- 日期：2026-10-09
- 目标：封闭多会话并发下的工具幂等身份、批次超时后台副作用、检查点失败观测和扩展结果校验边界，并确认 Kernel 调用级状态隔离。
- 判定时间：2026-10-09 +08:00
- 上线状态：开发未上线
- 判定方式：自动判定
- 判定证据：模块仍为 `1.0.17-SNAPSHOT`，最新提交未包含于发布标签，仓内及 kset-rag 均无模块调用方。
- 证据来源：Git ref `b6fdba0`、`kset-agent-core/pom.xml`、本记录 `agent-core-20261009-06`
- 当时代码状态：Kernel 运行状态按调用隔离且共享扩展要求线程安全；工具文档只使用决策内唯一 callId；标准并发批次超时后可能继续后台执行；失败终态检查点异常不可观测；工具注册重复时静默覆盖。
- 兼容选择：直接替换（用户于 2026-10-09 明确要求继续修复并检查外部并发调用隔离）
- 回滚目标：`agent-core-20261009-06`
- 基线 Git ref：`b6fdba0`
- 结果 Git ref：N/A
- 回滚来源：Git ref `b6fdba0`
- 回滚可用性：可用

| 文件 | 符号 | 职责 | 动作 | 状态 |
| --- | --- | --- | --- | --- |
| `kset-agent-core/src/main/java/com/kset/agent/core/tool/AgentToolOperationId.java` | 工具操作身份 | 固定 runId 与 callId 的结构化幂等作用域 | add | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/tool/AgentToolContext.java` | `operationId` | 向工具适配器暴露稳定复合身份 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/action/StandardActionHandlers.java` | 并发批次与工具观测 | 未知结果暂停并固定输出工具调用身份 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/action/AgentActionResult.java` | 动作结果不变量 | 限定终态状态与 answer 组合 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/action/AgentObservation.java` | Observation 不变量 | 固定成功、失败、进展和错误码组合 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/action/ToolCallAction.java` | 工具调用身份 | 规范化 callId、taskId 和 toolName | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/tool/ToolExecutionResult.java` | 工具结果不变量 | 固定成功、失败、进展和错误码组合 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/AgentErrorCode.java` | 工具错误枚举 | 新增未知结果与幂等冲突错误码 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/event` | 检查点失败事件与回调 | 暴露失败终态持久化异常并隔离 Listener 故障 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentLoopKernel.java` | 检查点、停止与扩展结果边界 | 统一失败观测、核验停止和 Listener critical 异常隔离 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/stop/AgentStopReason.java` | 核验停止枚举 | 固定 `RECONCILIATION_REQUIRED` 出口 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/strategy/ReactReasoningStrategy.java` | Observation 提示上下文 | 向默认 ReAct 暴露调用身份和错误信息 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/tool/InMemoryAgentToolRegistry.java` | `register` | 重复工具名构建期失败 | modify | active |
| `kset-agent-core/README.md`、`kset-agent-core/docs` | 并发、幂等和观测契约 | 固定宿主接入要求 | modify | active |
| `.claude/skills/kset-framework-coder/references/project-spec.md` | Agent 核心模块约定 | 固定并发隔离与未知结果边界 | modify | active |
| `CHANGELOG.md` | v1.0.17-SNAPSHOT | 记录并发与幂等边界加固 | modify | active |

### 回滚步骤

1. 从 Git ref `b6fdba0` 恢复本轮涉及的工具、事件、循环和文档文件。
2. 恢复 callId 单字段说明、原批次失败 Observation 和原检查点事件集合。
3. 将 `agent-core-20261009-06` 标为 active，并将本轮实现标为 rollback-candidate。

### 静态自查

- 请求幂等：已实现结构化 `(runId, callId)` 操作身份；callId 在 run 内只标识一个操作，工具适配器保存工具名与规范化参数指纹，同键异参拒绝。
- 任务抢占：会话单飞、owner/fencing 和跨节点租约继续由宿主保证；Kernel 只隔离独立调用状态。
- 数据权威源：工具权威结果与成功 Checkpoint 仍是恢复依据；批次未知结果不得自动重试。
- 一致性：批次超时、线程中断或提交异常且结果未知时固定以 `RECONCILIATION_REQUIRED + SUSPENDED` 停止，优先于同批次等待期间的通用中断或超时，避免后台副作用与下一轮并行；失败检查点对外可观测。
- 重试与恢复：结果未知时宿主按复合操作身份查询权威结果，同键异参必须拒绝。
- 并发隔离：Kernel 不保存跨 invocation 可变状态；共享扩展、Clock、Executor 及嵌套载荷仍须满足线程安全或不可变契约；标准工具 Observation 携带调用身份，批次结果可逐项关联。
- 验证方式：仅执行源码调用点、公开枚举、并发状态字段和文档映射静态检查；按用户要求不运行测试、不编译。

## agent-core-20261009-08

- 日期：2026-10-09
- 目标：封闭工具适配器主动返回 `TOOL_RESULT_UNKNOWN` 时单工具和批次仍继续循环的缺口，统一进入结果核验暂停。
- 判定时间：2026-10-09 +08:00
- 上线状态：开发未上线
- 判定方式：自动判定
- 判定证据：模块仍为 `1.0.17-SNAPSHOT`，基线提交未包含于发布标签，仓内及 kset-rag 均无模块调用方。
- 证据来源：Git ref `b6fdba0`、当前未提交的 `agent-core-20261009-07` 工作区、`kset-agent-core/pom.xml`
- 当时代码状态：批次 Handler 自身超时、中断或汇总异常会暂停；但工具适配器返回 `ToolExecutionResult.failure(TOOL_RESULT_UNKNOWN, ...)` 时，单工具和已完成批次只生成普通失败 Observation 并可继续下一轮。
- 兼容选择：直接替换（用户于 2026-10-09 明确要求修复）
- 回滚目标：`agent-core-20261009-07`
- 基线 Git ref：`b6fdba0` + 未提交的 `agent-core-20261009-07` 工作区
- 结果 Git ref：N/A
- 回滚来源：Git ref `b6fdba0` + 未提交工作区
- 回滚可用性：可用

| 文件 | 符号 | 职责 | 动作 | 状态 |
| --- | --- | --- | --- | --- |
| `kset-agent-core/src/main/java/com/kset/agent/core/action/StandardActionHandlers.java` | 单工具与批次结果处理 | 将适配器报告的未知结果统一转换为核验暂停 | modify | active |
| `kset-agent-core/README.md` | 工具未知结果契约 | 明确适配器分类与暂停语义 | modify | active |
| `kset-agent-core/docs/protocols/agent-json-v1.md` | 标准工具动作语义 | 固定单工具和批次未知结果出口 | modify | active |
| `kset-agent-core/docs/integration/kset-rag.md` | kset-rag 工具适配 | 明确远程未知结果返回与核验要求 | modify | active |
| `.claude/skills/kset-framework-coder/references/project-spec.md` | Agent 核心模块约定 | 固定工具主动报告未知结果的停止规则 | modify | active |
| `CHANGELOG.md` | v1.0.17-SNAPSHOT | 记录未知结果闭环 | modify | active |

### 回滚步骤

1. 恢复 `agent-core-20261009-07` 中只处理批次 Handler 自身未知异常的逻辑。
2. 移除工具返回未知结果时的单调用 pending 和完整批次 pending 分支。
3. 将 `agent-core-20261009-07` 标为 active，并将本轮实现标为 rollback-candidate。

### 静态自查

- 请求幂等：继续使用 `(runId, callId)` 与参数指纹；未知结果禁止生成新 callId 或盲目重试。
- 任务抢占：不适用；会话单飞、owner/fencing 和跨节点租约继续由宿主保证。
- 数据权威源：工具适配器的结构化结果负责区分明确失败与结果未知；未知结果恢复前必须查询工具权威结果。
- 一致性：单工具或批次任一结果为 `TOOL_RESULT_UNKNOWN` 时保存对应 pending action，并固定以 `RECONCILIATION_REQUIRED + SUSPENDED` 停止。
- 重试与恢复：普通明确失败仍交给 Strategy 决定后续动作；未知结果只允许核验后复用原操作身份恢复。
- 验证方式：仅执行源码调用点、错误枚举和文档映射静态检查；按用户要求不运行测试、不编译。

## agent-core-20261009-09

- 日期：2026-10-09
- 目标：补齐未知工具结果的确定性恢复输入，并加强多会话并发下结构化载荷的对象隔离。
- 判定时间：2026-10-09 +08:00
- 上线状态：开发未上线
- 判定方式：自动判定
- 判定证据：模块仍为 `1.0.17-SNAPSHOT`，基线提交未包含于发布标签，仓内及 kset-rag 均无模块调用方。
- 证据来源：Git ref `b6fdba0`、当前未提交的 `agent-core-20261009-08` 工作区、`kset-agent-core/pom.xml`
- 当时代码状态：Kernel 调用局部状态已隔离，但结构化载荷仅复制顶层容器；`RECONCILIATION_REQUIRED` 快照可以不注入权威结果直接恢复并再次进入模型循环。
- 兼容选择：直接替换（用户于 2026-10-09 明确确认继续优化方案）
- 回滚目标：`agent-core-20261009-08`
- 基线 Git ref：`b6fdba0` + 未提交的 `agent-core-20261009-08` 工作区
- 结果 Git ref：N/A
- 回滚来源：Git ref `b6fdba0` + 未提交工作区
- 回滚可用性：可用

| 文件 | 符号 | 职责 | 动作 | 状态 |
| --- | --- | --- | --- | --- |
| `kset-agent-core/src/main/java/com/kset/agent/core/api/AgentResumeInput.java` | 核验恢复输入 | 携带宿主权威工具 Observation | add | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentResumeReconciler.java` | pending 核验 | 校验完整调用身份、清理 pending 并合并权威结果 | add | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/value/AgentValueSnapshot.java` | 结构化载荷快照 | 递归复制 Map、List、Set 和数组并阻止循环引用 | add | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentLoopKernel.java` | `resume` 重载 | 在模型循环前应用确定性核验输入 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core` | 公开数据载体 | 接入递归结构化值快照 | modify | active |
| `kset-agent-core/README.md`、`kset-agent-core/docs` | 恢复与隔离契约 | 固定宿主输入、错误码和对象隔离边界 | modify | active |
| `.claude/skills/kset-framework-coder/references/project-spec.md` | Agent 核心模块约定 | 固定确定性恢复和递归载荷隔离 | modify | active |
| `CHANGELOG.md` | v1.0.17-SNAPSHOT | 记录恢复与隔离增强 | modify | active |

### 回滚步骤

1. 移除三参数 `resume`、`AgentResumeInput` 和 pending 核验器，恢复允许空输入恢复未知结果快照的旧行为。
2. 移除 `AgentValueSnapshot`，将公开数据载体恢复为顶层集合复制。
3. 将 `agent-core-20261009-08` 标为 active，并将本轮实现标为 rollback-candidate。

### 静态自查

- 请求幂等：核验结果必须完整匹配 pending 的 `callId/taskId/toolName`，继续沿用 `(runId, callId)` 与参数指纹作为权威工具身份。
- 任务抢占：会话单飞、owner/fencing 和跨节点租约仍由宿主保证；未增加只能覆盖单进程的内核锁。
- 数据权威源：`AgentResumeInput` 只接收宿主从工具权威源取得的最终 Observation，仍未知的结果禁止恢复。
- 一致性：核验输入缺项、重复、非 pending 或身份不一致时在模型与工具调用前拒绝；校验成功后清理 pending 并由初始检查点持久化。
- 重试与恢复：普通 resume 保持原入口；`RECONCILIATION_REQUIRED` 必须使用三参数入口注入完整结果。
- 并发隔离：调用局部状态不变；结构化 Map、List、Set 和数组递归快照，不透明 DTO 仍由宿主保证不可变或按调用隔离。
- 验证方式：仅执行源码调用点、类型引用、括号、导入和文档映射静态检查；按用户要求不运行测试、不编译。

## agent-core-20261009-10

- 日期：2026-10-09
- 目标：为循环主流程、停止出口、核验恢复、协议映射、递归载荷隔离和多 run 并发补充可执行模拟用例。
- 判定时间：2026-10-09 +08:00
- 上线状态：开发未上线
- 判定方式：自动判定
- 判定证据：模块仍为 `1.0.17-SNAPSHOT`，尚无正式发布和仓内调用方；此前实现只有静态核验，没有模块测试目录。
- 证据来源：Git ref `b6fdba0`、当前未提交的 `agent-core-20261009-09` 工作区、`kset-agent-core/pom.xml`
- 当时代码状态：生产流程已实现，但缺少并发调用、状态隔离、完整动作循环、停止原因、未知结果核验恢复和协议严格性的自动化模拟。
- 兼容选择：兼容增量；仅新增测试与测试约定，生产 API 保持不变，测试发现的缺陷按原契约修复。
- 回滚目标：`agent-core-20261009-09`
- 基线 Git ref：`b6fdba0` + 未提交的 `agent-core-20261009-09` 工作区
- 结果 Git ref：N/A
- 回滚来源：Git ref `b6fdba0` + 未提交工作区
- 回滚可用性：可用

| 文件 | 符号 | 职责 | 动作 | 状态 |
| --- | --- | --- | --- | --- |
| `kset-agent-core/src/test/java/com/kset/agent/core/loop/AgentLoopKernelFlowTest.java` | 循环模拟 | 完整动作、暂停恢复、停止与观测流程 | add | active |
| `kset-agent-core/src/test/java/com/kset/agent/core/loop/AgentLoopKernelConcurrencyTest.java` | 并发模拟 | 共享 Kernel 多 run 隔离、取消与批次并发 | add | active |
| `kset-agent-core/src/test/java/com/kset/agent/core/value/AgentValueSnapshotTest.java` | 载荷隔离 | 递归复制、只读容器与循环引用 | add | active |
| `kset-agent-core/src/test/java/com/kset/agent/core/protocol/json/AgentJsonV1CodecTest.java` | 协议模拟 | 六类动作、严格错误与注册边界 | add | active |
| `.claude/skills/kset-framework-coder/references/project-spec.md` | Agent 测试约定 | 固定纯内存测试边界 | modify | active |

### 回滚步骤

1. 删除本轮新增的 `kset-agent-core/src/test` 用例。
2. 恢复测试前的 project-spec 测试约定。
3. 将 `agent-core-20261009-09` 标为 active，并将本轮标为 rollback-candidate。

### 验收范围

- 请求幂等：模拟稳定 `(runId, callId)`、未知结果暂停与权威结果恢复。
- 任务抢占：不模拟宿主会话租约；仅验证一个 Kernel 并发处理不同 run 时状态不串线。
- 数据权威源：恢复用例显式构造权威 ToolExecutionResult，再通过 AgentResumeInput 注入。
- 一致性：覆盖检查点、pending、Observation、停止原因和最终快照。
- 重试与恢复：覆盖协议纠错、确认恢复、核验恢复及无效恢复拒绝。
- 验证方式：执行用户明确授权的最小模块测试 `mvn -pl kset-agent-core test`，不启动应用或外部服务。

### 验收结果

- 测试结果：`mvn -pl kset-agent-core test` 通过，共 22 个用例，0 failure、0 error、0 skipped。
- 循环流程：覆盖计划、单工具、Observation、最终回答、协议纠错、确认暂停恢复和未知结果核验恢复。
- 停止出口：覆盖完成、等待输入、核验暂停、用户取消、线程中断、活动超时、容量、最大轮次、协议错误上限、无进展、技术故障和宿主策略停止。
- 并发隔离：屏障强制共享 Kernel 的两个 run 同时进入模型，验证状态、快照、取消信号、invocationId 和事件序列隔离；双线程工具批次验证真实并行、复合操作身份和未知结果暂停。
- 协议扩展：覆盖内置六类动作、严格 envelope/字段/metadata 错误、自定义协议注册和内置协议不可覆盖。
- 对象隔离：覆盖 Map、List、Set、数组的递归快照、原对象后续修改隔离、只读结构和循环引用拒绝。
- 故障闭环：覆盖模型异常的结构化失败与失败快照、检查点异常和终态事件顺序；测试过程中未发现生产代码缺陷，仅修正测试断言的泛型写法与测试 Listener 的错误事件记录。

## agent-core-20261009-11

- 日期：2026-10-09
- 目标：修复工具确认未绑定完整操作、跨轮 callId 冲突未识别、立即停止叠加检查点失败时异常逃逸、无效策略完成状态及扩展未知结果误入标准核验的问题。
- 判定时间：2026-10-09 +08:00
- 上线状态：开发未上线
- 判定方式：自动判定
- 判定证据：模块仍为 `1.0.17-SNAPSHOT`，当前实现尚未提交且仓内及 kset-rag 均无调用方。
- 证据来源：Git ref `b6fdba0`、当前未提交的 `agent-core-20261009-10` 工作区、代码审查结果
- 当时代码状态：确认仅检查 callId；callId 只在单个 decision 内去重；立即停止后的 finish 检查点异常可能逃逸；Policy 可构造无 answer 的 COMPLETED；自定义未知结果会被误判为标准工具核验。
- 兼容选择：直接替换（用户明确要求继续优化；模块开发未上线）
- 回滚目标：`agent-core-20261009-10`
- 基线 Git ref：`b6fdba0` + 未提交的 `agent-core-20261009-10` 工作区
- 结果 Git ref：N/A
- 回滚来源：Git ref `b6fdba0` + 未提交工作区
- 回滚可用性：可用

| 文件 | 符号 | 职责 | 动作 | 状态 |
| --- | --- | --- | --- | --- |
| `kset-agent-core/src/main/java/com/kset/agent/core/action/ToolCallAction.java` | `operationIdentity` | 固定工具名、任务和参数身份 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentRunState.java` | 工具操作账本 | 跨轮保存 callId 对应的固定操作身份 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentLoopKernel.java` | 幂等、停止与核验边界 | 拒绝跨轮身份冲突并封闭检查点异常 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/action/StandardActionHandlers.java` | 确认消费 | 完整匹配 pending 工具或确认动作 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/stop/AgentStopController.java` | Policy 终态校验 | 拒绝无答案的策略完成状态 | modify | active |
| `kset-agent-core/src/test/java/com/kset/agent/core/loop/AgentLoopKernelFlowTest.java` | 缺陷回归模拟 | 覆盖确认篡改、跨轮冲突、复合故障和扩展核验 | modify | active |

### 回滚步骤

1. 恢复 `agent-core-20261009-10` 的 decision 内 callId 校验、确认判断、stopOrFail 和核验判断。
2. 移除内部工具操作账本及对应 ReAct 过滤逻辑。
3. 将 `agent-core-20261009-10` 标为 active，并将本轮标为 rollback-candidate。

### 静态自查

- 请求幂等：Kernel 在 run 级快照账本中固定 `callId/taskId/toolName/arguments`，同 ID 异身份以可纠正协议错误拒绝；工具适配器仍负责持久化参数指纹。
- 确认边界：普通确认和危险工具确认都必须完整匹配 `agent.pendingAction`；新 run 预置确认 ID 无法绕过暂停，确认后的同一操作可依赖适配器幂等重试。
- 停止闭环：StopPolicy 无权返回 `COMPLETED`；取消、超时或线程中断与终态检查点失败重叠时返回 `FATAL_ERROR + CHECKPOINT_FAILED`，异常不逃出 `run/resume`。
- 扩展隔离：只有标准工具动作的未知结果进入 `RECONCILIATION_REQUIRED`；自定义动作保留自己的 pending 和恢复语义。
- 模型隔离：内部操作与确认账本从默认 ReAct 请求属性和 Prompt 中过滤。

### 验收结果

- 测试结果：`mvn -pl kset-agent-core test` 通过，共 29 个用例，0 failure、0 error、0 skipped。
- 新增覆盖：精确 pending 确认、确认后参数篡改、预确认绕过、相同操作重试、取消叠加检查点失败、无答案策略完成和扩展未知结果隔离。

## agent-core-20261010-12

- 日期：2026-10-10
- 目标：为完整循环观测增加全局雪花 runId、逻辑步骤 ID、父步骤关联和结构化步骤状态，使宿主日志能够打印全部步骤及对应操作并定位失败阶段。
- 判定时间：2026-10-10 +08:00
- 上线状态：开发未上线
- 判定方式：自动判定
- 判定证据：模块仍为 `1.0.17-SNAPSHOT`，当前实现尚未提交且仓内及 kset-rag 均无调用方。
- 证据来源：Git ref `b6fdba0`、当前未提交的 `agent-core-20261009-11` 工作区、代码审查结果
- 当时代码状态：已有 runId、invocationId 和逐事件 eventId，但 started/completed/failed 没有共享 stepId；模型与检查点只能依赖事件顺序配对；缺省 runId 使用 UUID，无法配置分布式雪花节点。
- 兼容选择：直接替换（用户确认步骤日志与入口雪花 ID 方案；模块开发未上线）
- 回滚目标：`agent-core-20261009-11`
- 基线 Git ref：`b6fdba0` + 未提交的 `agent-core-20261009-11` 工作区
- 结果 Git ref：N/A
- 回滚来源：Git ref `b6fdba0` + 未提交工作区
- 回滚可用性：可用

| 文件 | 符号 | 职责 | 动作 | 状态 |
| --- | --- | --- | --- | --- |
| `kset-agent-core/src/main/java/com/kset/agent/core/id` | ID 生成 SPI 与雪花实现 | 入口生成全局 runId，显式配置数据中心与工作节点 | add | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/api/AgentRequest.java` | runId 绑定 | 允许新运行在 Kernel 入口绑定 ID，恢复必须使用原 ID | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/event` | 步骤观测身份 | 增加 stepId、parentStepId、stepType、operation 和结构化状态 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/execution/AgentExecutionContext.java` | 扩展调用追踪信封 | 向 Model、Action、Tool 和 StopPolicy 下传 invocation 与当前步骤身份 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentKernelBuilder.java` | `runIdGenerator` | 注册线程安全的宿主 ID 生成器 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentLoopKernel.java` | 入口 ID 与步骤作用域 | 在首事件前绑定 runId，为各阶段生成稳定步骤身份 | modify | active |
| `kset-agent-core/README.md`、`kset-agent-core/docs/integration/kset-rag.md` | 日志映射 | 固定完整步骤日志字段与雪花节点配置边界 | modify | active |
| `.claude/skills/kset-framework-coder/references/project-spec.md` | Agent 观测约定 | 登记四层 ID 和日志安全契约 | modify | active |
| `CHANGELOG.md` | v1.0.17-SNAPSHOT | 记录步骤追踪与雪花入口 | modify | active |

### 回滚步骤

1. 恢复 `agent-core-20261009-11` 的生命周期上下文、事件生成和 UUID runId 行为。
2. 移除 ID 生成 SPI、雪花实现及 Builder 配置入口。
3. 将 `agent-core-20261009-11` 标为 active，并将本轮标为 rollback-candidate。

### 静态自查

- 请求幂等：工具副作用继续使用 `(runId, callId)`；新任务 runId 在首事件前一次性绑定，恢复必须使用快照原值。
- 任务抢占：雪花生成器线程安全，但 datacenterId/workerId 的跨实例唯一分配仍由宿主负责；会话单飞与租约边界不变。
- 数据权威源：生命周期日志是观测投影，Checkpoint 和工具权威结果仍是恢复依据，日志不得作为状态权威源。
- 一致性：runId、invocationId、stepId 和 eventId 四层身份职责分离；模型、决策、动作、检查点的开始与完成或失败共享 stepId，协议纠错轮次使用 TURN_FAILED 闭环。
- 单次调用追溯：Model Adapter 使用 model step，Action Handler 与 Tool Adapter 使用同一 action step，StopPolicy 使用检查时的活动 step；适配器日志与 Listener 事件共享 invocationId/stepId，工具以 callId 区分具体操作。
- 重试与恢复：ID 生成失败在执行前返回 `ID_GENERATION_FAILED`；resume 不调用生成器，避免恢复时产生新全局 ID。
- 并发隔离：雪花生成器以同步序列保证单实例并发唯一；步骤计数和活动步骤栈均为 invocation 局部状态，不跨 run 共享。
- 日志安全：统一 `onEvent` 输出步骤身份、操作和时序；工具与模型专用回调只追加白名单字段，不记录原始 Prompt、参数、结果或请求属性。
- 验证方式：完成源码调用点、枚举穷尽、步骤栈配对、文档字段映射和 `git diff --check` 静态检查；按项目研发规则未编译、未运行测试。

## agent-core-20261010-13

- 日期：2026-10-10
- 目标：统一 Agent 核心公开参数、访问器、扩展点和专用对象说明，消除 action、tool、plan、error、stop、context 与扩展身份的同义命名。
- 判定时间：2026-10-10 +08:00
- 上线状态：开发未上线
- 判定方式：自动判定
- 判定证据：模块仍为 `1.0.17-SNAPSHOT`，当前实现尚未提交且仓内及 kset-rag 均无调用方。
- 证据来源：Git ref `b6fdba0`、当前未提交的 `agent-core-20261010-12` 工作区、公开 API 命名盘点
- 当时代码状态：循环与追踪逻辑已完成，但公开对象仍混用 type/actionType、id/taskId/callId、name/toolName、execution/executionContext、code/errorCode、stop/stopDecision 及泛化 Builder 参数名。
- 兼容选择：直接替换（用户要求统一当前核心 Agent 的参数、专用说明和对象；模块开发未上线）
- 回滚目标：`agent-core-20261010-12`
- 基线 Git ref：`b6fdba0` + 未提交的 `agent-core-20261010-12` 工作区
- 结果 Git ref：N/A
- 回滚来源：Git ref `b6fdba0` + 未提交工作区
- 回滚可用性：可用

| 文件 | 符号 | 职责 | 动作 | 状态 |
| --- | --- | --- | --- | --- |
| `kset-agent-core/src/main/java/com/kset/agent/core/action` | Action 与计划对象 | 统一 actionType、toolCalls、taskId、dependsOn 和结果字段语义 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/tool` | Tool 对象与注册表 | 统一 toolName、callId 和 executionContext | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/api` | Result 与 Failure | 统一 runStatus、stopDecision、errorCode 和 errorMessage | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/stop` | 停止对象 | 统一 shouldStop、stopReason、runStatus 和 stopMessage | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/protocol` | 协议扩展点 | 统一 protocolId 和 protocolErrorCode | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/strategy` | 推理扩展点 | 统一 strategyId | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/event` | 生命周期对象 | 统一 eventSequence 和专用字段说明 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/loop` | Kernel、Builder 与 RunState | 同步统一后的公开访问器和具名参数 | modify | active |
| `kset-agent-core/src/test` | 现有模拟用例 | 同步公开 API 名称，不新增行为用例 | modify | active |
| `kset-agent-core/README.md`、`kset-agent-core/docs` | 核心对象词汇表 | 固定 Java API 术语与 JSON wire 边界 | modify | active |
| `.claude/skills/kset-framework-coder/references/project-spec.md` | Agent 命名约定 | 登记核心对象标准术语 | modify | active |
| `CHANGELOG.md` | v1.0.17-SNAPSHOT | 记录公开命名收敛 | modify | active |

### 回滚步骤

1. 根据本轮工作区差异恢复 `agent-core-20261010-12` 的公开字段、访问器、扩展点和内部引用。
2. 恢复命名调整前的 README、协议说明、project-spec 和测试源码引用。
3. 将 `agent-core-20261010-12` 标为 active，并将本轮标为 rollback-candidate。

### 静态自查

- 请求幂等：仅统一工具身份名称，结构化 `(runId, callId)`、参数指纹与冲突规则不变。
- 任务抢占：不适用；会话单飞、owner/fencing 和跨节点租约仍由宿主负责。
- 数据权威源：不变；Checkpoint 与工具权威结果仍是恢复依据。
- 一致性：Java API 统一命名并区分 AgentRunStatus 与 AgentLifecycleStatus；`agent-json:v1` wire 字段保持不变，Codec 继续显式映射。
- 重试与恢复：不改变 pending、核验输入、恢复校验或停止出口。
- 验证方式：已完成全部源码、测试源码与文档引用的旧访问器残留扫描，修复 ReAct 对旧 `id()` 的遗漏引用，`git diff --check` 通过；按项目研发规则不编译、不运行测试。

## agent-core-20261010-14

- 日期：2026-10-10
- 目标：收窄 Kernel 内部状态迁移与默认 ReAct 内部阶段的可见性，同时保留全部明确对外的扩展方法和兼容回调。
- 判定时间：2026-10-10 +08:00
- 上线状态：开发未上线
- 判定方式：自动判定
- 判定证据：模块仍为 `1.0.17-SNAPSHOT`，当前实现尚未提交，仓内及 kset-rag 均无调用方；用户明确要求公开扩展方法不按仓内调用次数清理。
- 证据来源：Git ref `b6fdba0`、当前未提交的 `agent-core-20261010-13` 工作区、代码审查结果
- 当时代码状态：`AgentRunState` 将 Kernel 专用状态迁移方法作为 public 暴露，`ReactPhase` 注释声明为内部类型但仍为 public；对外 Action、Registry 与 Listener 扩展方法均保持 active。
- 兼容选择：直接替换（用户确认优化审查问题；模块开发未上线）
- 回滚目标：`agent-core-20261010-13`
- 基线 Git ref：`b6fdba0` + 未提交的 `agent-core-20261010-13` 工作区
- 结果 Git ref：`c686867`
- 回滚来源：Git ref `b6fdba0` + 未提交工作区
- 回滚可用性：可用

| 文件 | 符号 | 职责 | 动作 | 状态 |
| --- | --- | --- | --- | --- |
| `kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentRunState.java` | Kernel 状态迁移方法 | 收窄为包级，仅保留扩展所需属性更新与只读访问器 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/strategy/ReactPhase.java` | 默认 ReAct 内部阶段 | 收窄为包级内部类型 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/action/AgentAction.java` | `metadata` | 保留公开扩展方法，不以仓内调用次数作为删除依据 | retain | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/action/AgentActionRegistry.java` | `supports` | 保留公开查询方法，不以仓内调用次数作为删除依据 | retain | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/event/AgentLifecycleListener.java` | 兼容回调 | 保留可达的公开兼容桥 | retain | active-compatible |

### 回滚步骤

1. 恢复 `agent-core-20261010-13` 中 `AgentRunState` 状态迁移方法与 `ReactPhase` 的 public 可见性。
2. 保持循环入口、协议、快照格式和对外扩展方法不变。
3. 将 `agent-core-20261010-13` 标为 active，并将本轮标为 rollback-candidate。

### 静态自查

- 请求幂等：不变；工具操作继续使用结构化 `(runId, callId)` 身份。
- 任务抢占：不适用；会话单飞与跨节点租约仍由宿主负责。
- 数据权威源：不变；Checkpoint 与工具权威结果仍是恢复依据。
- 一致性：仅调整 Java 可见性，不改变状态转换顺序、快照内容或 `agent-json:v1` wire 契约。
- 重试与恢复：不改变 pending、核验输入、恢复校验或停止出口。
- 验证方式：已完成源码调用包边界、公开扩展方法保留、内部类型引用与 `git diff --check` 静态检查；按项目研发规则不编译、不运行测试。

## agent-core-20261010-15

- 日期：2026-10-10
- 目标：对模型适配器显式标记为可重试的瞬时调用失败执行有限重试。
- 判定时间：2026-10-10 +08:00
- 上线状态：开发未上线
- 判定方式：自动判定
- 判定证据：上一变更记录标记开发未上线；模块仍为 `1.0.17-SNAPSHOT`，仓内无业务调用方或该版本的生产发布映射。
- 证据来源：Git ref `c686867`、`kset-agent-core/pom.xml`、项目内调用点检索及当前工作区
- 当时代码状态：`AgentLoopKernel#run/resume` 在模型步骤内只调用一次 `generateModel`；失败直接进入结构化 `AgentFailure`；`AgentCoreException` 已有 `retryable` 标记；已有未提交的步骤观测修正和测试改动。
- 兼容选择：兼容增量；仅对 `MODEL_CALL_FAILED` 且 `retryable=true` 的模型异常启用默认重试，其余现有异常行为不变。
- 回滚目标：`agent-core-20261010-14`
- 基线 Git ref：`c686867` + 当前未提交工作区
- 结果 Git ref：`d028b66`
- 回滚来源：`kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentLoopKernel.java#generateModel` 单次调用路径；已提交基线为 `c686867`
- 回滚可用性：可用

| 文件 | 符号 | 职责 | 动作 | 状态 |
| --- | --- | --- | --- | --- |
| `kset-agent-core/src/main/java/com/kset/agent/core/model/AgentModelRetryOptions.java` | `AgentModelRetryOptions` | 配置最多尝试次数和退避上限 | add | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentKernelBuilder.java` | `modelRetry` | 注册线程安全的模型重试配置 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentLoopKernel.java` | `generateModelWithRetry` | 在单个模型步骤内有限重试并遵守停止决定 | modify | active |
| `kset-agent-core/src/test/java/com/kset/agent/core/loop/AgentLoopKernelFlowTest.java` | 模型重试场景 | 覆盖成功、耗尽、不可重试、取消及期限 | modify | active |
| `kset-agent-core/README.md` | 模型重试说明 | 明确适配器标记与停止边界 | modify | active |
| `.claude/skills/kset-framework-coder/references/project-spec.md` | Agent 模型重试约定 | 固定可重试错误与工具重试边界 | modify | active |

### 回滚步骤

1. 在模型步骤恢复直接调用 `generateModel`，移除重试等待与 Builder 配置。
2. 保持现有模型失败结果、生命周期步骤及工具核验路径不变。
3. 将 `agent-core-20261010-14` 恢复为 active，并把本轮标为 rollback-candidate。

### 静态自查

- 请求幂等：模型生成无业务写入；工具操作仍按 `(runId, callId)` 隔离，不因模型重试自动重放工具。
- 任务抢占：不适用；会话单飞、租约与 fencing 继续由宿主负责。
- 数据权威源：Checkpoint 和工具权威结果保持不变，模型重试不修改已确认状态。
- 一致性：单个 MODEL step 涵盖全部尝试，只有最终成功或失败改变循环结果。
- 重试与恢复：最多 3 次模型调用；只重试显式可重试的 `MODEL_CALL_FAILED`，指数退避带抖动，停止条件优先于后续尝试。
- 验证方式：已静态核对重试次数、异常分类、取消/中断/deadline 边界、Builder 配置与单 MODEL 步骤身份；新增成功、耗尽、不可重试、错误码不匹配、后续失败转不可重试、停止边界及配置校验用例。按研发规则未编译、未运行测试。

## agent-core-20261010-16

- 日期：2026-10-10
- 目标：明确 Java API `runId` 与宿主 `agentRunId` 的一一映射，避免与会话、计划任务和单次调用 ID 混淆。
- 判定时间：2026-10-10 +08:00
- 上线状态：不涉及兼容
- 判定方式：N/A
- 判定证据：仅修改文档与项目约定，Java API、快照字段和值均不变。
- 证据来源：`kset-agent-core/README.md`、`kset-agent-core/docs`、`kset-agent-core/src/main/java`
- 当时代码状态：`runId` 已在请求、结果、快照、执行上下文与工具操作身份中作为公开 Java 字段；本轮无源码调用方迁移。
- 兼容选择：N/A
- 回滚目标：`agent-core-20261010-15`
- 基线 Git ref：`c686867` + 当前未提交工作区
- 结果 Git ref：`d028b66`
- 回滚来源：N/A
- 回滚可用性：不可用

| 文件 | 符号 | 职责 | 动作 | 状态 |
| --- | --- | --- | --- | --- |
| `.claude/skills/kset-framework-coder/references/project-spec.md` | Agent 核心模块约定 | 固定内核字段与宿主字段映射 | modify | active |
| `kset-agent-core/README.md` | 核心对象命名与接入示例 | 明确 Agent 任务身份 | modify | active |
| `kset-agent-core/docs/protocol-extension-guide.md` | 生命周期观测 | 明确命名边界与模型重试耗时 | modify | active |
| `kset-agent-core/docs/protocols/agent-json-v1.md` | 执行追踪信封 | 明确协议不承载任务身份 | modify | active |
| `kset-agent-core/docs/integration/kset-rag.md` | Agent 任务身份 | 规范宿主字段与会话关系 | modify | active |

### 回滚步骤

1. 如需恢复旧文案，按本轮文件索引逐处恢复文档；Java API 与快照不需要回滚。

### 静态自查

- 请求幂等：仍使用结构化 `(runId, callId)`，宿主仅把同一 `runId` 映射为 `agentRunId` 字段。
- 任务抢占：会话 `sessionId` 单飞仍由宿主负责，未改变内核行为。
- 数据权威源：快照与工具权威结果不变；不新建第二套任务 ID。
- 一致性：请求、结果、快照和事件中的 `runId` 保持同值，未更改序列化字段。
- 重试与恢复：恢复沿用原快照 `runId`；模型重试行为不变。
- 验证方式：文档静态核对；不编译、不运行测试。

## agent-core-20261010-17

- 日期：2026-10-10
- 目标：统一公开对象与字段语义，拆分最终回答与暂停提示，固定字段所有者和作用域。
- 判定时间：2026-10-10 +08:00
- 上线状态：开发未上线
- 判定方式：自动判定
- 判定证据：`v1.0.16` 不含该模块，当前模块版本为 `1.0.17-SNAPSHOT`，仓内未发现业务调用方；仓外调用方未知。
- 证据来源：Git tag `v1.0.16`、`kset-agent-core/pom.xml`、仓内源码搜索
- 当时代码状态：`AgentLoopKernel#run/resume` 为入口；`AgentActionResult.answer` 同时承载最终回答与暂停提示，后者进入 `AgentResult.answer` 和 v1 快照；已有未提交模型重试、生命周期与文档改动，迁移状态未知。
- 兼容选择：直接替换（用户确认开发未上线阶段直接收敛；Java record 构造与 v1 快照不提供兼容）。
- 回滚目标：`agent-core-20261010-16`
- 基线 Git ref：`c686867` + 当前未提交工作区
- 结果 Git ref：`d028b66`
- 回滚来源：Git ref `c686867` + 当前未提交工作区
- 回滚可用性：可用

| 文件 | 符号 | 职责 | 动作 | 状态 |
| --- | --- | --- | --- | --- |
| `kset-agent-core/src/main/java/com/kset/agent/core/action/AgentActionResult.java` | `answer/suspensionMessage` | 终态动作内容分离 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentRunState.java` | `restore/apply/stopped/snapshot` | 分离最终回答和暂停状态 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentRunState.java` | `restoreForRollback/applyForRollback/stoppedForRollback` | 保留旧状态映射，须连同 v1 快照契约恢复 | retain | rollback-candidate |
| `kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentRunSnapshot.java` | `CURRENT_VERSION` | v2 快照字段契约 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/api/AgentResult.java` | `answer` | 完成态最终回答约束 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/stop/AgentStopController.java` | `evaluate` | 暂停提示映射到停止消息 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/action/StandardActionHandlers.java` | 标准暂停动作 | 传递暂停提示 | modify | active |
| `kset-agent-core/docs/core-object-contract.md` | 字段词典 | 公开对象与字段单源契约 | add | active |
| `kset-agent-core/src/test/java/com/kset/agent/core/loop/AgentLoopKernelFlowTest.java` | 暂停与恢复断言 | 锁定回答和等待提示分离 | modify | active |
| `kset-agent-core/README.md`、`kset-agent-core/docs` | 接入说明 | 指向字段词典并固定暂停语义 | modify | active |
| `.claude/skills/kset-framework-coder/references/project-spec.md` | Agent 核心模块约定 | 登记字段单源与快照版本 | modify | active |
| `CHANGELOG.md` | Unreleased | 记录字段契约收敛 | modify | active |

### 回滚步骤

1. 参考 `AgentRunState#restoreForRollback/applyForRollback/stoppedForRollback`，并从本轮基线恢复动作结果、停止决定和 v1 快照结构；这些保留方法不能在 v2 生产路径直接调用。
2. 恢复旧文档字段说明，并更新本记录中的实现状态。

### 静态自查

- 请求幂等：工具 `(runId, callId)` 与参数身份不变；本轮只调整终态内容归属。
- 任务抢占：不适用；会话单飞和跨节点租约仍由宿主负责。
- 数据权威源：宿主检查点与工具权威结果不变；v1 快照须由宿主迁移后方可作为 v2 恢复输入。
- 一致性：`answer` 仅对应完成态；暂停提示显式保存并通过停止消息返回，协议 wire 不变。
- 重试与恢复：模型重试及工具核验路径不变；恢复清除上次等待提示，保留原 runId。
- 验证方式：静态引用与差异检查；按项目研发规则不编译、不运行测试。

## agent-core-20261010-18

- 日期：2026-10-10
- 目标：阻止未确认的标准待处理动作在恢复时越过确认，收紧内置协议任务 ID 类型，并将计划依赖校验改为迭代线性遍历。
- 判定时间：2026-10-10 +08:00
- 上线状态：不涉及兼容（恢复既定确认及严格协议契约）
- 判定方式：N/A
- 判定证据：公开 Java API、快照 v2 与有效 `agent-json:v1` wire 形状不变；现有文档已规定完整待处理动作确认与字符串任务 ID。
- 证据来源：`kset-agent-core/docs/core-object-contract.md`、`kset-agent-core/docs/protocols/agent-json-v1.md`、Git ref `d028b66`
- 当时代码状态：`AgentLoopKernel#resume` 对普通暂停无确认前置校验，恢复后的决策可绕开 `agent.pendingAction`；Codec 将非字符串 `taskId` 转成字符串；计划依赖使用递归且反复查找任务。
- 兼容选择：N/A；仅修复无效输入与违约恢复路径，不改变有效调用契约。
- 回滚目标：`agent-core-20261010-17`
- 基线 Git ref：`d028b66`
- 结果 Git ref：N/A
- 回滚来源：Git ref `d028b66`
- 回滚可用性：可用

| 文件 | 符号 | 职责 | 动作 | 状态 |
| --- | --- | --- | --- | --- |
| `kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentResumeReconciler.java` | `apply/validatePendingDecision` | 恢复确认及待处理动作身份校验 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentLoopKernel.java` | `resume/execute` | 在模型调用前接入恢复确认与决策校验 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/protocol/json/AgentJsonV1Codec.java` | `toolCall` | 严格解析可选 taskId | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/action/TaskPlanAction.java` | `hasCycle` | 迭代线性校验计划依赖 | modify | active |
| `kset-agent-core/src/test/java/com/kset/agent/core` | 恢复、协议及深计划用例 | 覆盖本轮边界 | modify | active |
| `kset-agent-core/README.md`、`kset-agent-core/docs` | 核心契约说明 | 明确确认入口、字段类型和计划校验 | modify | active |
| `.claude/skills/kset-framework-coder/references/project-spec.md` | Agent 核心模块约定 | 固定恢复确认前置约束 | modify | active |

### 回滚步骤

1. 使用 Git ref `d028b66` 恢复本轮所列源码与文档，再核对确认入口与协议约定。
2. 将 `agent-core-20261010-17` 恢复为 active，本轮标为 rollback-candidate。

### 静态自查

- 请求幂等：结构化 `(runId, callId)` 与原工具账本保持不变；确认前不会开始工具调用，批次仍逐项确认并由宿主工具执行持久幂等。
- 任务抢占：不适用；会话单飞和跨节点租约仍由宿主负责。
- 数据权威源：Checkpoint 快照与宿主工具权威结果保持原有所有权；`RECONCILIATION_REQUIRED` 恢复仍须完整权威 Observation。
- 一致性：缺少确认 ID 在模型调用前失败；已确认但重述动作不匹配计入协议错误，其他动作不能清理 pending；计划依赖仍校验唯一、引用与无环。
- 重试与恢复：模型有限重试逻辑不变，协议不匹配仍按既有 `protocolErrorLimit` 纠正；批次逐项确认与未知结果核验路径不变。
- 验证方式：已静态核对恢复前置、决策匹配、字段解析、迭代图遍历、回归用例和 `git diff --check`；按研发规则不编译、不运行测试。

## agent-core-20261010-19

- 日期：2026-10-10
- 目标：初始化阶段收敛 Agent Core 公开命名，消除运行身份、确认类型、动作停止状态、活动时限、工具幂等键及事件状态的歧义。
- 判定时间：2026-10-10 +08:00
- 上线状态：开发未上线（沿用模块尚未接入宿主的既有记录；本轮未获得新的生产发布证据）
- 判定方式：用户确认项目初始化阶段可不保留命名兼容
- 判定证据：用户本轮要求直接按命名建议优化；现有记录注明 kset-rag 尚未接入。
- 证据来源：本轮用户确认、`kset-agent-core/README.md`、本记录 `agent-core-20261010-18`
- 当时代码状态：入口为 `AgentLoopKernel#run/resume`；公开身份仍名为 `runId`，确认 ID 共用单键，动作终止状态、活动超时、工具操作身份及事件状态存在同名异义；已知调用方为模块内测试与待接入 kset-rag 文档。
- 兼容选择：本阶段不处理兼容；不保留旧 Java 访问器、确认键或 v2 快照恢复。
- 回滚目标：`agent-core-20261010-18`
- 基线 Git ref：`d028b66` + 本轮前未提交工作区
- 结果 Git ref：N/A
- 回滚来源：Git ref `d028b66` + 未持久化的本轮前工作区
- 回滚可用性：不可用；本轮前的未提交内容没有独立 Git ref

| 文件 | 符号 | 职责 | 动作 | 状态 |
| --- | --- | --- | --- | --- |
| `kset-agent-core/src/main/java/com/kset/agent/core/api`、`loop`、`execution`、`event` | 公开运行身份与事件状态 | 统一 `agentRunId`、`eventStatus` 并升级版本 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/action`、`tool` | 动作结果与工具操作 | 分离请求停止状态、操作定义及幂等键 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/id/AgentRunIdGenerator.java`、`SnowflakeAgentRunIdGenerator.java` | Agent 运行 ID 生成 | 专用生成器名称及 `nextAgentRunId` | add | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/id/AgentIdGenerator.java`、`SnowflakeAgentIdGenerator.java` | 旧通用生成器名称 | 不保留兼容别名 | remove | removed |
| `kset-agent-core/src/main/java/com/kset/agent/core/tool/AgentToolIdempotencyKey.java` | 工具幂等键 | 专用类型和结构化 `(agentRunId, callId)` | add | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/tool/AgentToolOperationId.java` | 旧操作 ID 类型 | 不保留兼容别名 | remove | removed |
| `kset-agent-core/src/test/java` | 命名契约用例 | 同步公开 API 引用与 v3 快照 | modify | active |
| `kset-agent-core/README.md`、`kset-agent-core/docs`、`.claude/skills/kset-framework-coder/references/project-spec.md` | 字段契约 | 更新单源定义与接入说明 | modify | active |

### 回滚步骤

1. 从本轮前工作区及 Git ref `d028b66` 恢复 Agent Core 公开类型、字段和对应文档。
2. 将 `agent-core-20261010-18` 恢复为 active，并将本轮标为 rollback-candidate。

### 静态自查

- 请求幂等：工具持久化键现为 `AgentToolIdempotencyKey(agentRunId, callId)`，操作内容仍由 `operationDefinition()` 与参数指纹核对；未改副作用调用顺序。
- 任务抢占：不适用，仍由宿主负责会话单飞与跨节点执行权。
- 数据权威源：宿主快照和工具权威结果不变；快照 v3 明确拒绝旧 v1/v2。
- 一致性：恢复前分别校验 `confirmationId` 与 `callId` 对应的批准键；交叉传入会在模型调用前得到 `INVALID_RESUME_INPUT`。
- 重试与恢复：模型失败重试、工具未知结果核验逻辑未改；旧快照需要显式迁移后才能恢复。
- 验证方式：静态搜索旧名称、核对类型文件名、`git diff --check`；按研发规则未编译、未运行测试。

## agent-core-20261010-20

- 日期：2026-10-10
- 目标：固定 v4 快照的完整批次批准与未知结果核验状态，收紧恢复身份和无标记控制 JSON 的边界，并同步文档。
- 判定时间：2026-10-10 +08:00
- 上线状态：开发未上线
- 判定方式：自动判定
- 判定证据：模块仍为 `1.0.17-SNAPSHOT`；已知发布标签 `v1.0.16` 不含该模块，仓内尚无业务调用方，kset-rag 接入仍为文档映射。
- 证据来源：`kset-agent-core/pom.xml`、`kset-agent-core/docs/integration/kset-rag.md`、Git ref `2a8af7c`
- 当时代码状态：入口为 `AgentLoopKernel#run/resume`，基线为 v3 快照；标准批次在逐项批准时尚未固定完整批次，核验检查点失败后的 `FAILED` 快照可能缺少强制核验标记；已知调用方为模块内测试。
- 兼容选择：本阶段不处理兼容；v1/v2/v3 快照不直接按 v4 恢复，宿主持久化数据须显式迁移。
- 回滚目标：`agent-core-20261010-19`
- 基线 Git ref：`2a8af7c`
- 结果 Git ref：`1a79aab`
- 回滚来源：Git ref `2a8af7c`
- 回滚可用性：可用

| 文件 | 符号 | 职责 | 动作 | 状态 |
| --- | --- | --- | --- | --- |
| `kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentRunSnapshot.java` | `CURRENT_VERSION/validatePendingState` | v4 快照字段与待处理状态校验 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentResumeReconciler.java` | `apply/validatePendingDecision/validateObservation` | 批次恢复与核验身份校验 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/action/StandardActionHandlers.java` | 标准工具批次与核验处理 | 固定完整批次批准和未知结果标记 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/api/AgentResumeInput.java` | `reconciledObservations` | 明确权威工具结果输入 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/protocol/json/AgentJsonV1Codec.java` | `looksLikeControlJson` | 截断的无标记控制 JSON 不作最终回答 | modify | active |
| `kset-agent-core/src/test/java/com/kset/agent/core` | 快照、批次、核验与协议边界用例 | 补充回归场景 | modify | active |
| `kset-agent-core/README.md`、`kset-agent-core/docs` | 对外契约 | 统一 v4、恢复错误码和身份说明 | modify | active |
| `CHANGELOG.md` | Unreleased | 汇总本轮对外契约 | modify | active |

### 回滚步骤

1. 从 Git ref `2a8af7c` 恢复本轮源码与契约文档；已生成的 v4 快照不能直接交给回滚后的 v3 内核，须由宿主先处理持久化数据。
2. 将 `agent-core-20261010-19` 恢复为 active，并更新本记录中的当前入口与实现状态。

### 静态自查

- 请求幂等：标准工具仍以结构化 `(agentRunId, callId)` 和操作定义核对；批次批准不触发提前执行。
- 任务抢占：不适用；会话单飞与跨节点租约由宿主负责。
- 数据权威源：核验结果由宿主按原工具幂等键查询权威结果，再经 `AgentResumeInput` 注入；core 不主动查询。
- 一致性：v4 快照校验 pending 批次与核验标记；恢复前拒绝不完整或非字符串的调用身份。
- 重试与恢复：模型重试未变；未知工具结果必须核验，v1/v2/v3 快照须显式迁移。
- 验证方式：静态差异及文档核对；未编译、未运行测试。

## agent-core-20261023-21

- 日期：2026-10-23
- 目标：限制默认 ReAct 送入模型的观察内容和提示文本，保留快照中的原始观察结果。
- 判定时间：2026-10-23 +08:00
- 上线状态：开发未上线
- 判定方式：自动判定
- 判定证据：模块仍为 `1.0.17-SNAPSHOT`，已知 `v1.0.16` 发布版不含该模块；本仓未发现业务宿主调用，现有接入为文档约定。
- 证据来源：`kset-agent-core/pom.xml`、`CHANGELOG.md`、`kset-agent-core/docs/integration/kset-rag.md`、Git ref `96cf32e`
- 当时代码状态：`ReactReasoningStrategy#userPrompt` 把完整观察输出拼入模型提示，`ModelRequest.attributes.observations` 持有原始观察列表；`AgentLoopKernel#execute` 在协议补充提示后无输入字符上限。已知调用方为模块内测试。
- 兼容选择：本阶段不处理兼容；默认 ReAct 的模型输入会变为受限摘要，`run/resume`、结果与快照公开契约保持不变。
- 回滚目标：`agent-core-20261010-20`
- 基线 Git ref：`96cf32e`
- 结果 Git ref：`cd925c9`
- 回滚来源：Git ref `96cf32e`
- 回滚可用性：可用

| 文件 | 符号 | 职责 | 动作 | 状态 |
| --- | --- | --- | --- | --- |
| `kset-agent-core/src/main/java/com/kset/agent/core/strategy/ReactReasoningStrategy.java` | `nextTurn/userPrompt` | 默认 ReAct 模型输入 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentLoopOptions.java` | `maxPromptChars/maxObservationChars` | 字符容量配置 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentLoopKernel.java` | `execute` | 模型调用前容量停止 | modify | active |
| `kset-agent-core/src/test/java/com/kset/agent/core/loop/AgentLoopKernelFlowTest.java` | 观察边界与容量场景 | 回归用例 | modify | active |
| `kset-agent-core/README.md`、`kset-agent-core/docs/core-object-contract.md` | 模型输入边界 | 对外说明 | modify | active |
| `CHANGELOG.md`、`.claude/skills/kset-framework-coder/references/project-spec.md` | 版本说明和项目事实 | 同步本轮约定 | modify | active |

### 回滚步骤

1. 从 Git ref `96cf32e` 恢复本轮 Agent Core 源码、测试和契约说明；返回结果与 v4 快照格式无需迁移。
2. 将 `agent-core-20261010-20` 恢复为 active，并更新本记录的当前入口。

### 静态自查

- 请求幂等：本轮不修改工具调用身份、批准或持久化幂等键。
- 任务抢占：不适用；会话单飞和跨节点租约仍属宿主。
- 数据权威源：快照保留原始观察结果；受限摘要只用于模型输入。
- 一致性：提示容量检查发生在模型调用前，并遵守取消、线程中断及活动超时优先级；不改变已有检查点写入顺序。观察区标题与省略计数纳入总预算。
- 重试与恢复：不修改模型重试及 `run/resume` 的快照契约。
- 验证方式：已核对源码入口、旧九参数构造器调用点、观察摘要按最近优先收集、完整文本临界长度、准备阶段取消、大量历史省略、标准工具错误码、可容纳的完整调用 ID、超长 ID 回退与特殊字符边界测试源码及 `git diff --check`；按研发规则未执行编译或测试。

## agent-core-20261023-22

- 日期：2026-10-23
- 目标：区分模型可见观察摘要与快照原始观察的字段名，并明确字符限额的计量与作用范围。
- 判定时间：2026-10-23 23:36:39 +08:00
- 上线状态：开发未上线
- 判定方式：自动判定
- 判定证据：模块仍为 `1.0.17-SNAPSHOT`，`v1.0.16` 不含该模块；仓内仅测试引用旧摘要键与旧配置访问器。
- 证据来源：`kset-agent-core/pom.xml`、`kset-agent-core/src/test/java`、Git ref `cd925c9`、Git tag `v1.0.16`
- 当时代码状态：默认 ReAct 的 `ModelRequest.attributes.observations` 保存有界字符串摘要，`AgentLoopOptions.maxObservationChars` 限制单条摘要；`AgentRunSnapshot.observations` 保存原始结构化观察。已知调用方仅为模块内测试，未见迁移中的宿主调用。
- 兼容选择：本阶段不处理兼容；旧模型属性键和旧配置访问器不保留别名，`AgentRunSnapshot.observations` 与快照版本不变。
- 回滚目标：`agent-core-20261023-21`
- 基线 Git ref：`cd925c9`
- 结果 Git ref：N/A（本轮未提交）
- 回滚来源：Git ref `cd925c9`
- 回滚可用性：可用

| 文件 | 符号 | 职责 | 动作 | 状态 |
| --- | --- | --- | --- | --- |
| `kset-agent-core/src/main/java/com/kset/agent/core/strategy/ReactReasoningStrategy.java` | `OBSERVATION_SUMMARIES_ATTRIBUTE/nextTurn` | 模型可见摘要属性名 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentLoopOptions.java` | `maxObservationSummaryChars/maxPromptChars` | 摘要与提示字符限额定义 | modify | active |
| `kset-agent-core/src/test/java/com/kset/agent/core/loop/AgentLoopKernelFlowTest.java` | 观察摘要与限额用例 | 核对新键及旧键消失 | modify | active |
| `kset-agent-core/README.md`、`kset-agent-core/docs/core-object-contract.md`、`.claude/skills/kset-framework-coder/references/project-spec.md` | 公开字段与项目事实 | 说明 UTF-16 单位和原始观察边界 | modify | active |
| `CHANGELOG.md` | Unreleased 条目 | 标明新字段名 | modify | active |

### 回滚步骤

1. 从 Git ref `cd925c9` 恢复旧模型属性键、配置访问器和对应文档；v4 快照无需迁移。
2. 将 `agent-core-20261023-21` 恢复为 active，并更新本记录中的当前入口。

### 静态自查

- 请求幂等、任务抢占与数据权威源：工具调用和快照原始观察不变；摘要仅为模型输入投影。
- 一致性：新摘要键与配置访问器在实现、测试和公开契约中一致；`maxPromptChars` 仍只检查 Codec 准备后的两段提示。
- 重试与恢复：不修改模型重试、停止优先级或 v4 快照恢复契约。
- 验证方式：静态差异和旧名称搜索；按研发规则未执行编译或测试。

## agent-core-20261024-23

- 日期：2026-10-24
- 目标：消除 Agent Core 模型属性与待处理工具调用访问器的语义歧义，并明确进展标记的停止计数含义。
- 判定时间：2026-10-24 +08:00
- 上线状态：开发未上线
- 判定方式：自动判定
- 判定证据：模块为 `1.0.17-SNAPSHOT`，发布标签 `v1.0.16` 不含该模块；已知调用方仅为本模块测试与接入文档。
- 证据来源：`kset-agent-core/pom.xml`、`kset-agent-core/src/test/java`、`kset-agent-core/docs/integration/kset-rag.md`、Git ref `cd925c9`
- 当时代码状态：入口为 `AgentLoopKernel#run/resume`；`ModelRequest.attributes` 由策略填入循环上下文，但契约称为适配器专用；`pendingToolCall()` 只返回单个待处理动作，批次需用 `pendingToolCalls()`；`progress` 参与连续无进展计数。上一轮字段更名尚未提交，已知调用方仅为模块内测试，宿主迁移未开始；现实现为 active。
- 兼容选择：本阶段不处理兼容；单数访问器更名且不保留旧别名，v4 快照格式与 `progress` 字段不变。
- 回滚目标：`agent-core-20261023-22`
- 基线 Git ref：`cd925c9` + 本轮前未提交工作区
- 结果 Git ref：N/A（本轮未提交）
- 回滚来源：Git ref `cd925c9` + 本轮前未提交工作区
- 回滚可用性：可用

| 文件 | 符号 | 职责 | 动作 | 状态 |
| --- | --- | --- | --- | --- |
| `kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentRunSnapshot.java` | `pendingSingleToolCall/pendingToolCalls` | 单个与完整批次只读视图 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/model/ModelRequest.java`、`AgentModel.java` | `attributes/generate` | 策略上下文与适配器责任 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/tool/ToolExecutionResult.java`、`kset-agent-core/src/main/java/com/kset/agent/core/action/AgentObservation.java` | `progress` | 无进展计数语义 | modify | active |
| `kset-agent-core/src/test/java/com/kset/agent/core/loop/AgentLoopKernelFlowTest.java` | 待处理调用断言 | 单个与批次边界 | modify | active |
| `kset-agent-core/README.md`、`kset-agent-core/docs/core-object-contract.md`、`kset-agent-core/docs/integration/kset-rag.md`、`CHANGELOG.md` | 对外字段说明 | 同步 Java API 契约 | modify | active |

### 回滚步骤

1. 基于 Git ref `cd925c9` 与本轮前未提交工作区恢复访问器名称及文档；v4 快照无需迁移。
2. 将 `agent-core-20261023-22` 恢复为 active，并更新当前入口。

### 静态自查

- 请求幂等、任务抢占与数据权威源：本轮不修改工具执行、批准、核验或宿主权威结果边界。
- 一致性：单数访问器只返回单个待处理工具动作；批次仍由复数访问器完整返回。`progress` 字段与 v4 快照格式不变。
- 重试与恢复：本轮不修改模型重试、停止判断或恢复流程；`ModelRequest.attributes` 仍由策略填充，由适配器选择使用。
- 验证方式：静态搜索旧访问器、检查单个与批次断言源码、文档契约和 `git diff --check`；按研发规则未编译、未运行测试。

## agent-core-20261102-24

- 日期：2026-11-02
- 目标：复用由 Core 自身冻结的属性 Map，减少状态转换与检查点生成时的重复递归复制。
- 判定时间：2026-11-02 +08:00
- 上线状态：开发未上线
- 判定方式：自动判定
- 判定证据：模块仍为 `1.0.17-SNAPSHOT`；当前公开契约与 v4 快照格式不变。
- 证据来源：`kset-agent-core/pom.xml`、`kset-agent-core/docs/core-object-contract.md`、Git ref `8ac1c1b`
- 当时代码状态：`AgentValueSnapshot#map` 对已冻结的内部 Map 仍逐层复制；Core 每次状态转换及检查点投影均调用该方法。已知业务调用方通过 Runtime 接入，公开字段与恢复格式不变。
- 兼容选择：N/A；仅优化内部复制，保留公开 Map 不可变、外部可变输入隔离及 null 校验。
- 回滚目标：`agent-core-20261024-23`
- 基线 Git ref：`8ac1c1b6ba2c4d45ba3ec38055a48cf806414c3f`
- 结果 Git ref：N/A（本轮未提交）
- 回滚来源：Git ref `8ac1c1b6ba2c4d45ba3ec38055a48cf806414c3f`
- 回滚可用性：可用

| 文件 | 符号 | 职责 | 动作 | 状态 |
| --- | --- | --- | --- | --- |
| `kset-agent-core/src/main/java/com/kset/agent/core/value/AgentValueSnapshot.java` | `map/snapshotMap` | 冻结属性并复用已冻结结构 | modify | active |
| `kset-agent-core/src/test/java/com/kset/agent/core/value/AgentValueSnapshotTest.java` | 冻结复用与可变隔离用例 | 回归约束 | modify | active |

### 回滚步骤

1. 从 Git ref `8ac1c1b` 恢复本轮 Core 值快照实现与用例；公开 API 和 v4 快照无需迁移。
2. 将 `agent-core-20261024-23` 恢复为 active，并更新本记录的当前入口。

### 静态自查

- 请求幂等、任务抢占、数据权威源：不修改工具操作身份、宿主单飞或权威结果边界。
- 一致性：仅复用由 `AgentValueSnapshot` 自身创建的不可变 Map；外部 Map 仍递归复制，顶层 null 校验及工具字段显式 null 保持原语义。
- 并发与恢复：冻结 Map 无可变共享入口，Core 的同步默认 Executor、工具超时和 v4 快照字段保持不变。
- 性能边界：减少已冻结属性树在后续状态和检查点中的递归复制；观察列表追加仍随历史长度线性复制，实际收益尚未经运行测量。
- 验证方式：静态核对相关源码与回归用例、`git diff --check`；按项目规则未编译或运行测试。

## agent-core-20261104-25

- 日期：2026-11-04
- 目标：修复默认 ReAct 首个回答分片被拒绝的问题，并压缩各阶段重复的工具目录与协议提示。
- 判定时间：2026-11-04 11:48:43 +08:00
- 上线状态：开发未上线
- 判定方式：自动判定
- 判定证据：模块仍为 `1.0.17-SNAPSHOT`；已发布 `v1.0.16` 不含 Agent Core；本轮不改变 `agent-json:v1` wire 字段。
- 证据来源：`kset-agent-core/pom.xml`、`kset-agent-core/docs/protocols/agent-json-v1.md`、Git tag `v1.0.16`、Git ref `5bc753897dc4c63f4c126d623cd07f9e46fbc88e`
- 当时代码状态：`AgentLoopKernel#run/resume` 调用默认 `ReactReasoningStrategy#nextTurn/validate` 与 `AgentJsonV1Codec#prepare/decode`；默认 ReAct 在无既有回答分片时拒绝 `answer_chunk`，各阶段均向模型重复投影完整工具目录，Codec 每轮追加六种动作的完整示例。`ReactReasoningStrategy.java` 已有与本轮无关的未提交回滚方法清理，本轮保留。
- 兼容选择：N/A；仅修复策略校验和改变模型提示投影，新增可选的 `ModelRequest` 属性常量，保持既有 Java API 签名、Codec 解码、v1 wire 与快照 v4 契约。
- 回滚目标：`agent-core-20261102-24`
- 基线 Git ref：`5bc753897dc4c63f4c126d623cd07f9e46fbc88e` + 本轮前未提交工作区
- 结果 Git ref：N/A（本轮未提交）
- 回滚来源：Git ref `5bc753897dc4c63f4c126d623cd07f9e46fbc88e` + 本轮前未提交工作区
- 回滚可用性：可用

| 文件 | 符号 | 职责 | 动作 | 状态 |
| --- | --- | --- | --- | --- |
| `kset-agent-core/src/main/java/com/kset/agent/core/strategy/ReactReasoningStrategy.java` | `nextTurn/allowedTypes/systemPrompt/userPrompt` | 阶段动作与工具目录提示 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/model/ModelRequest.java` | `ALLOWED_STANDARD_ACTION_TYPES_ATTRIBUTE` | 传递可用的内置动作提示 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/protocol/json/AgentJsonV1Codec.java` | `prepare` | 按允许动作缩减协议提示 | modify | active |
| `kset-agent-core/src/test/java/com/kset/agent/core/loop/AgentLoopKernelFlowTest.java`、`kset-agent-core/src/test/java/com/kset/agent/core/protocol/json/AgentJsonV1CodecTest.java` | 分片与提示用例 | 守护阶段转换及提示范围 | modify | active |
| `kset-agent-core/README.md`、`kset-agent-core/docs/protocols/agent-json-v1.md`、`kset-agent-core/docs/core-object-contract.md`、`CHANGELOG.md`、`.claude/skills/kset-framework-coder/references/project-spec.md` | 默认 ReAct 与 v1 协议说明 | 记录提示投影边界 | modify | active |

### 回滚步骤

1. 从基线 Git ref 恢复策略提示与协议准备逻辑，保留本轮前未提交的回滚方法清理。
2. 恢复相关用例和说明，并更新本记录的当前入口。

### 静态自查

- 请求幂等与数据权威源：不改工具分发、调用身份或快照中的原始 Observation。
- 一致性：允许动作与 Codec 缩减提示使用同一阶段列表；无效或缺失列表回退完整 v1 提示。首个回答分片进入回答阶段后只允许分片或最终回答；规划阶段省略工具 schema，回答阶段省略工具目录。v1 解码、工具操作身份与快照字段未修改。
- 验证方式：检查源码和用例逻辑、`git diff --check`；按本技能规则未编译或运行测试。

## agent-core-20261104-26

- 日期：2026-11-04
- 目标：减少每轮模型请求中可证明重复或空值的提示内容，并支持请求提供固定系统提示前缀。
- 判定时间：2026-11-04 12:11:19 +08:00
- 上线状态：开发未上线
- 判定方式：自动判定
- 判定证据：Agent Core 仍为 `1.0.17-SNAPSHOT`，已发布 `v1.0.16` 不含该模块；现有变更 `agent-core-20261104-25` 尚未提交。
- 证据来源：`kset-agent-core/pom.xml`、Git tag `v1.0.16`、Git ref `5bc753897dc4c63f4c126d623cd07f9e46fbc88e`
- 当时代码状态：`AgentRequest` 只允许四类请求侧 `agent.*` 批准/拒绝键；默认 ReAct 每轮在系统提示重复列动作，用户提示重述阶段与计划创建标记，观察摘要输出空错误字段与回答分片副本。`AgentJsonV1Codec#prepare` 追加协议格式，Kernel 按准备后的字符数限额校验。
- 兼容选择：兼容增量；保留现有构造器、字段、v1 wire 和快照 v4，新增可选的请求前缀入口。
- 回滚目标：`agent-core-20261104-25`
- 基线 Git ref：`5bc753897dc4c63f4c126d623cd07f9e46fbc88e` + 本轮前未提交工作区
- 结果 Git ref：N/A（本轮未提交）
- 回滚来源：Git ref `5bc753897dc4c63f4c126d623cd07f9e46fbc88e` + 本轮前未提交工作区
- 回滚可用性：可用

| 文件 | 符号 | 职责 | 动作 | 状态 |
| --- | --- | --- | --- | --- |
| `kset-agent-core/src/main/java/com/kset/agent/core/api/AgentRequest.java` | 系统提示前缀请求键与访问方法 | 可信请求前缀输入 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentLoopKernel.java` | `prepareModelRequest/applySystemPromptPrefix` | 前置策略系统提示后交给 Codec | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/strategy/ReactReasoningStrategy.java` | `nextTurn/systemPrompt/userPrompt/observationView/renderObservation` | 每轮模型提示投影 | modify | active |
| `kset-agent-core/src/test/java/com/kset/agent/core/loop/AgentLoopKernelFlowTest.java` | 前缀与提示用例 | 核对输入和摘要边界 | modify | active |
| `kset-agent-core/README.md`、`kset-agent-core/docs/core-object-contract.md`、`CHANGELOG.md`、`.claude/skills/kset-framework-coder/references/project-spec.md` | 请求与提示说明 | 同步可选前缀及摘要语义 | modify | active |

### 回滚步骤

1. 恢复请求前缀入口、ReAct 提示投影与对应文档至本轮基线，保留此前其他未提交改动。
2. 更新当前实现索引和状态；v1 wire 与快照无需迁移。

### 静态自查

- 请求幂等和恢复：工具调用身份、批准、快照原始观察及 v1 wire 未修改。Kernel 在调用 Codec 前前置请求前缀；该前缀只在请求属性中传递，不写入快照，恢复调用须重新提供。
- 模型输入：阶段提示缩短，用户提示省略重复的阶段与计划标记；观察摘要仅省略空字段和已在状态中的回答分片，保留工具错误码、调用 ID、待处理动作及不可信数据边界。`ModelRequest.attributes.state` 仍保留完整投影供适配器显式选择。
- 验证方式：静态核对源码、用例及文档，执行 `git diff --check`；按本技能规则未编译或运行测试。

## agent-core-20261104-27

- 日期：2026-11-04
- 目标：供应商适配器提供真实 token 窗口后，Core 在模型调用前自动压缩默认 ReAct 的观察摘要。
- 判定时间：2026-11-04 12:33:15 +08:00
- 上线状态：开发未上线
- 判定方式：自动判定
- 判定证据：模块仍为 `1.0.17-SNAPSHOT`；已发布 `v1.0.16` 不含 Agent Core；本仓无供应商模型适配器。
- 证据来源：`kset-agent-core/pom.xml`、Git tag `v1.0.16`、Git ref `5bc753897dc4c63f4c126d623cd07f9e46fbc88e`、`kset-agent-core/src/main/java/com/kset/agent/core/model/AgentModel.java`
- 当时代码状态：`AgentLoopKernel#run/resume` 在 Codec 准备提示后只检查 `maxPromptChars`，超出立即停止；默认 ReAct 的观察区固定占字符预算一半。`AgentModel` 无 token 计数扩展；已知调用方仅通过 Runtime 注入宿主模型实现，供应商适配器尚未接入；本轮前实现为 active。
- 兼容选择：兼容增量；保留 `AgentModel` 函数式接口、既有策略和 Codec 行为，token 容量为可选适配器能力。
- 回滚目标：`agent-core-20261104-26`
- 旧版本正式线上主分支及对比基线 Git SHA：`main`；`v1.0.16` 发布提交待确认，本轮 Agent Core 不在该发布版本中。
- 旧版正式发布版本：`v1.0.16`；Agent Core 尚未发布。
- 基线 Git ref：`5bc753897dc4c63f4c126d623cd07f9e46fbc88e` + 本轮前未提交工作区
- 结果 Git ref：N/A（本轮未提交）
- 回滚来源：Git ref `5bc753897dc4c63f4c126d623cd07f9e46fbc88e` + 本轮前未提交工作区
- 回滚可用性：可用

| 文件 | 符号 | 职责 | 动作 | 状态 |
| --- | --- | --- | --- | --- |
| `kset-agent-core/src/main/java/com/kset/agent/core/model/AgentModelCapacity.java`、`ModelPromptCapacity.java` | 可选模型容量契约 | 提供真实输入 token 数与可用上限 | add | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/strategy/AgentReasoningStrategy.java`、`ReactReasoningStrategy.java` | 提示压缩入口与默认实现 | 缩减旧观察并保留必要上下文 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentLoopKernel.java` | 模型前容量检查 | 超限时重新准备提示并尝试压缩 | modify | active |
| `kset-agent-core/src/test/java/com/kset/agent/core/loop/AgentLoopKernelFlowTest.java` | 容量边界用例 | 覆盖压缩、不可压缩及停止优先级 | modify | active |
| `kset-agent-core/README.md`、`kset-agent-core/docs/core-object-contract.md`、`.claude/skills/kset-framework-coder/references/project-spec.md` | 对外契约 | 说明适配器和 Core 的职责 | modify | active |

### 回滚步骤

1. 从本轮基线恢复容量扩展入口及 Kernel 压缩逻辑，保留此前未提交的提示精简与前缀输入改动。
2. 更新实现索引；v1 wire 和 v4 快照无需迁移。

### 静态自查

- 请求幂等、任务抢占、数据权威源：本轮仅改变模型可见观察摘要；工具调用身份、原始 Observation、快照与权威结果边界未修改。
- 一致性：每次压缩后重新应用固定前缀、Codec 与容量检查；适配器计数使用收紧输出上限后的请求。最近观察的必要字段无法容纳时停止压缩，自定义策略没有压缩实现时沿用容量停止。
- 重试与恢复：模型未调用前完成容量判断；取消、超时与线程中断优先。恢复时按当前请求重新计算容量，不修改快照版本。
- 验证方式：静态核对源码、边界用例和 `git diff --check`；按研发规则未编译或运行测试。

## agent-core-20261104-28

- 日期：2026-11-04
- 目标：内置多厂商模型容量，按实际模型 ID 限制调用前的输出预算，并公开可查询模型列表。
- 判定时间：2026-11-04 12:39:46 +08:00
- 上线状态：开发未上线
- 判定方式：自动判定
- 判定证据：Agent Core 仍为 `1.0.17-SNAPSHOT`，已发布 `v1.0.16` 不含该模块；现有模型容量压缩改动尚未提交。
- 证据来源：`kset-agent-core/pom.xml`、Git tag `v1.0.16`、Git ref `5bc753897dc4c63f4c126d623cd07f9e46fbc88e`
- 当时代码状态：`AgentLoopKernel#run/resume` 每轮准备 Prompt 后检查字符限额，并可由可选 `AgentModelCapacity` 核对供应商实际输入 Token；`AgentModel` 无预调用模型名，调用方需自行提供输出上限。
- 兼容选择：兼容增量；保留 `AgentModel` 函数式接口、既有构造器、`agent-json:v1` wire 字段及快照 v4，新增默认模型名方法与只读目录。
- 回滚目标：`agent-core-20261104-27`
- 基线 Git ref：`5bc753897dc4c63f4c126d623cd07f9e46fbc88e` + 本轮前未提交工作区
- 结果 Git ref：N/A（本轮未提交）
- 回滚来源：Git ref `5bc753897dc4c63f4c126d623cd07f9e46fbc88e` + 本轮前未提交工作区
- 回滚可用性：可用

| 文件 | 符号 | 职责 | 动作 | 状态 |
| --- | --- | --- | --- | --- |
| `kset-agent-core/src/main/java/com/kset/agent/core/model/BuiltInModels.java`、`ModelCapacity.java` | `list/find`、容量字段 | 内置公开模型目录 | add | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/model/AgentModel.java`、`ModelRequest.java` | `modelName/withMaxOutputTokens` | 模型识别与输出上限副本 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentLoopKernel.java` | `limitModelRequest/fitModelRequest` | 调用前限制已知模型预算 | modify | active |
| `kset-agent-core/src/test/java/com/kset/agent/core/loop/AgentLoopKernelFlowTest.java` | 已知模型与匿名模型用例 | 核对兼容路径 | modify | active |
| `kset-agent-core/README.md`、`kset-agent-core/docs/core-object-contract.md`、`.claude/skills/kset-framework-coder/references/project-spec.md`、`CHANGELOG.md` | 模型容量说明 | 同步公开契约与官方来源 | modify | active |

### 回滚步骤

1. 从本轮前工作区移除模型目录及默认模型名方法，恢复 Kernel 的容量检查入口与 `ModelRequest` 副本方法；保留此前已存在的 Token 压缩逻辑。
2. 更新实现索引；协议和快照无需迁移。

### 静态自查

- 模型目录仅匹配完整 ID，容量来源为官方模型页；未知模型保持原请求，已知模型将输出上限限制在目录上限与文本预算内。
- 适配器继续校验完整供应商请求的实际 Token，目录不覆盖服务端具体账户、平台或模态的额外限制。
- 验证方式：静态检查源码、契约与差异；按项目规则未编译或运行测试。

## agent-core-20261104-29

- 日期：2026-11-04
- 目标：将有官方容量规格的旧版模型纳入公开目录，包括 `gpt-6-sol`。
- 判定时间：2026-11-04 14:15:54 +08:00
- 上线状态：开发未上线
- 判定方式：自动判定
- 判定证据：Agent Core 为 `1.0.17-SNAPSHOT`，已发布 `v1.0.16` 不含该模块；目录扩展属于尚未发布的 Java API。
- 证据来源：`kset-agent-core/pom.xml`、Git tag `v1.0.16`、Git ref `5bc753897dc4c63f4c126d623cd07f9e46fbc88e`
- 当时代码状态：`BuiltInModels` 仅含 9 个当前模型；`find` 只识别完整 ID，旧版 `gpt-6-sol` 返回空。
- 兼容选择：兼容增量；仅向只读目录追加精确 ID，不改变调用签名、协议或快照。
- 回滚目标：`agent-core-20261104-28`
- 基线 Git ref：`5bc753897dc4c63f4c126d623cd07f9e46fbc88e` + 本轮前未提交工作区
- 结果 Git ref：N/A（本轮未提交）
- 回滚来源：Git ref `5bc753897dc4c63f4c126d623cd07f9e46fbc88e` + 本轮前未提交工作区
- 回滚可用性：可用

| 文件 | 符号 | 职责 | 动作 | 状态 |
| --- | --- | --- | --- | --- |
| `kset-agent-core/src/main/java/com/kset/agent/core/model/BuiltInModels.java` | `MODELS` | 增补旧版模型 ID 与容量 | modify | active |
| `kset-agent-core/src/test/java/com/kset/agent/core/loop/AgentLoopKernelFlowTest.java` | `knownModelUsesBuiltInOutputLimitWithoutChangingAnonymousModels` | 核对旧版容量差异 | modify | active |
| `kset-agent-core/README.md`、`CHANGELOG.md`、`.claude/skills/kset-framework-coder/references/project-spec.md` | 旧版目录说明 | 记录别名与弃用边界 | modify | active |

### 回滚步骤

1. 从 `BuiltInModels.MODELS` 移除本轮旧版条目，恢复目录用例与说明；保留 `agent-core-20261104-28` 的目录 API 和容量限制。
2. 更新实现索引；协议和快照无需迁移。

### 静态自查

- `gpt-6-sol`、GPT-5.6 与 GPT-4.1 容量按 OpenAI 官方模型页登记；Claude 4.5 与 Gemini 旧版按各自官方模型页登记。
- `gpt-5.6` 是官方别名；Gemini 2.5 与 GPT-4.1 的弃用状态仅作说明，目录容量不表示账号可用性。
- 验证方式：核对目录、文档与差异；按项目规则未编译或运行测试。

## agent-core-20261104-30

- 日期：2026-11-04
- 目标：修复内置模型容量审查发现的固定模型绑定缺口、默认输出上限语义变化和 Claude 4.5 日期快照缺失。
- 判定时间：2026-11-04
- 上线状态：开发未上线
- 判定方式：自动判定
- 判定证据：Agent Core 为 `1.0.17-SNAPSHOT`；已发布 `v1.0.16` 不含该模块，本轮修改针对尚未发布的目录 API。
- 证据来源：`kset-agent-core/pom.xml`、Git tag `v1.0.16`、Git ref `5bc753897dc4c63f4c126d623cd07f9e46fbc88e`
- 当时代码状态：`AgentModel` 为函数式接口，lambda 无固定模型 ID 绑定入口；Kernel 对已知模型将 `maxOutputTokens=0` 改为目录最大输出值；目录仅有 Claude 4.5 短别名。相关入口为 active，调用方仍由 Runtime 注入模型实现，供应商适配器尚未接入。
- 兼容选择：兼容增量；保留原有构造器、函数式接口、协议 wire 字段和快照 v4；新增具名包装及 Runtime 重载，恢复 0 的未指定语义。
- 回滚目标：`agent-core-20261104-29`
- 基线 Git ref：`5bc753897dc4c63f4c126d623cd07f9e46fbc88e` + 本轮前未提交工作区
- 结果 Git ref：N/A（本轮未提交）
- 回滚来源：Git ref `5bc753897dc4c63f4c126d623cd07f9e46fbc88e` + 本轮前未提交工作区
- 回滚可用性：可用

| 文件 | 符号 | 职责 | 动作 | 状态 |
| --- | --- | --- | --- | --- |
| `kset-agent-core/src/main/java/com/kset/agent/core/model/AgentModel.java` | `named` | 为固定模型绑定 ID 并保留可选容量能力 | modify | active |
| `kset-agent-runtime/src/main/java/com/kset/agent/runtime/AgentRuntime.java` | `builder(modelName, model)` | 提供 Runtime 固定模型入口 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentLoopKernel.java` | `limitModelRequest` | 保留未指定输出语义，限制正数输出预算 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/model/BuiltInModels.java` | `MODELS` | 补齐 Claude 4.5 日期快照 ID | modify | active |
| `kset-agent-core/src/test/java/com/kset/agent/core/loop/AgentLoopKernelFlowTest.java`、`kset-agent-runtime/src/test/java/com/kset/agent/runtime/AgentRuntimeFlowTest.java` | 模型边界用例 | 覆盖绑定、容量透传、正数限额和未指定语义 | modify | active |
| `kset-agent-core/README.md`、`kset-agent-core/docs/core-object-contract.md`、`.claude/skills/kset-framework-coder/references/project-spec.md`、`CHANGELOG.md` | 对外说明 | 同步模型目录和限额契约 | modify | active |

### 回滚步骤

1. 从本轮前工作区恢复模型绑定 API、Kernel 输出上限判断与 Claude 日期快照条目；保留此前内置模型目录及其他容量检查。
2. 更新实现索引；协议和快照无需迁移。

### 静态自查

- 具名包装只固定模型 ID，原委托仍执行生成；委托实现 `AgentModelCapacity` 时包装继续暴露该能力。
- 已知模型保持文本窗口检查，正数输出上限按目录收紧；0 不再改写，实际供应商请求和输出预留仍由适配器核验。
- 验证方式：核对源码、边界用例与 `git diff --check`；按项目规则未编译或运行测试。

## agent-core-20261104-31

- 日期：2026-11-04
- 目标：允许业务方按模型 ID 配置上下文窗口和最大输出容量，同时保留内置目录作为默认值。
- 判定时间：2026-11-04
- 上线状态：开发未上线
- 判定方式：自动判定
- 判定证据：Agent Core 与 Runtime 均为 `1.0.17-SNAPSHOT`，已发布 `v1.0.16` 不含这两个模块；当前自定义容量入口尚不存在。
- 证据来源：`kset-agent-core/pom.xml`、`kset-agent-runtime/pom.xml`、Git tag `v1.0.16`、Git ref `5bc753897dc4c63f4c126d623cd07f9e46fbc88e`
- 当时代码状态：`AgentKernelBuilder` 和 `AgentRuntime.Builder` 仅接收模型适配器；`AgentLoopKernel#limitModelRequest/fitModelRequest` 直接查 `BuiltInModels`，未知模型没有目录容量，业务方无法覆盖内置值。现有入口为 active；调用方仍由宿主注入模型实现，供应商适配器尚未接入。
- 兼容选择：兼容增量；新增可选构建器方法，不改变既有构造器、协议 wire 字段、快照 v4 或未配置时的行为。
- 回滚目标：`agent-core-20261104-30`
- 基线 Git ref：`5bc753897dc4c63f4c126d623cd07f9e46fbc88e` + 本轮前未提交工作区
- 结果 Git ref：N/A（本轮未提交）
- 回滚来源：Git ref `5bc753897dc4c63f4c126d623cd07f9e46fbc88e` + 本轮前未提交工作区
- 回滚可用性：可用

| 文件 | 符号 | 职责 | 动作 | 状态 |
| --- | --- | --- | --- | --- |
| `kset-agent-core/src/main/java/com/kset/agent/core/model/ModelCapacityCatalog.java` | `withOverrides/list/find` | 组合内置与实例级自定义容量 | add | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentKernelBuilder.java` | `modelCapacity` | Core 构建期容量登记 | modify | active |
| `kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentLoopKernel.java` | `limitModelRequest/fitModelRequest` | 使用同一容量解析结果 | modify | active |
| `kset-agent-runtime/src/main/java/com/kset/agent/runtime/AgentRuntime.java` | `Builder.modelCapacity/kernel` | 将 Runtime 容量传给每次运行的 Kernel | modify | active |
| `kset-agent-core/src/test/java/com/kset/agent/core/loop/AgentLoopKernelFlowTest.java`、`kset-agent-runtime/src/test/java/com/kset/agent/runtime/AgentRuntimeFlowTest.java` | 自定义容量用例 | 覆盖覆盖、回退、隔离和无效配置 | modify | active |
| `kset-agent-core/README.md`、`kset-agent-core/docs/core-object-contract.md`、`.claude/skills/kset-framework-coder/references/project-spec.md`、`CHANGELOG.md` | 对外契约 | 说明容量配置与优先级 | modify | active |

### 回滚步骤

1. 移除本轮新增的目录组合类和构建器容量入口，恢复 Kernel 对 `BuiltInModels` 的直接查询；保留 `agent-core-20261104-30` 的具名适配器和内置目录。
2. 更新实现索引；协议与快照无需迁移。

### 静态自查

- 自定义容量按构建实例保存为不可变副本，覆盖内置同名 ID；重复登记按大小写无关的完整 ID 拒绝。
- 未登记 ID 回退内置目录；适配器仍负责完整供应商请求的实际 Token 计数和输出预留。
- 验证方式：静态核对源码、用例、契约与 `git diff --check`；按项目规则未编译或运行测试。

## agent-core-20261104-32

- 日期：2026-11-04
- 目标：修复动态模型适配器在输出上限收紧后改选模型、导致最终容量与实际模型不一致的问题，并防止自定义策略绕过正数调用上限。
- 判定时间：2026-11-04
- 上线状态：开发未上线
- 判定方式：自动判定
- 判定证据：Agent Core 为 `1.0.17-SNAPSHOT`，已发布 `v1.0.16` 不含该模块；当前 `fitModelRequest` 仅在原请求上解析模型 ID。
- 证据来源：`kset-agent-core/pom.xml`、Git tag `v1.0.16`、Git ref `5bc753897dc4c63f4c126d623cd07f9e46fbc88e`、`AgentLoopKernel#fitModelRequest`
- 当时代码状态：`fitModelRequest` 根据原 `ModelRequest` 查询一次容量，然后 `limitModelRequest` 可改写 `maxOutputTokens`；实际 `generate` 收到改写后的请求，而 `AgentModel.modelName(request)` 允许按该请求报告模型 ID。旧入口为 active。
- 兼容选择：兼容增量；保留模型、请求、协议、快照和默认 0 语义，仅在容量收紧后重新确认模型并再次收紧。
- 回滚目标：`agent-core-20261104-31`
- 基线 Git ref：`5bc753897dc4c63f4c126d623cd07f9e46fbc88e` + 本轮前未提交工作区
- 结果 Git ref：N/A（本轮未提交）
- 回滚来源：Git ref `5bc753897dc4c63f4c126d623cd07f9e46fbc88e` + 本轮前未提交工作区
- 回滚可用性：可用

| 文件 | 符号 | 职责 | 动作 | 状态 |
| --- | --- | --- | --- | --- |
| `kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentLoopKernel.java` | `fitModelRequest` | 收紧输出预算后重新解析实际模型容量 | modify | active |
| `kset-agent-core/src/test/java/com/kset/agent/core/loop/AgentLoopKernelFlowTest.java` | 模型容量用例 | 覆盖二次选模、最终上限及自定义策略上限 | modify | active |
| `kset-agent-core/README.md`、`kset-agent-core/docs/core-object-contract.md`、`.claude/skills/kset-framework-coder/references/project-spec.md` | 容量契约 | 说明动态选模在最终请求上的容量约束 | modify | active |

### 回滚步骤

1. 将 `fitModelRequest` 恢复为一次模型 ID 查询，并移除本轮动态模型用例和说明；保留实例级自定义目录。
2. 协议与快照无需迁移，更新实现索引。

### 静态自查

- 每次容量收紧只降低正数输出上限；重新查找模型直到上限稳定，最终的适配器 Token 检查使用同一模型容量。
- 正数调用选项对所有策略生效；策略提供 0 或过大值均不能绕过该上限，调用选项为 0 时仍不指定。
- 0 仍表示未指定；模型适配器仍须对完整供应商请求与真实输出预留自行校验。
- 验证方式：静态核对源码、用例与 `git diff --check`；按项目规则未编译或运行测试。
