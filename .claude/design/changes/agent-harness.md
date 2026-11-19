# Agent Harness 公共接入变更记录

- feature-key：`agent-harness`
- 当前 change-id：`agent-harness-20261119-02`
- 当前状态：`active`
- 当前入口：`kset-starter-agent-harness/src/main/java/com/kset/agent/harness/AgentHarnessFactory.java#builder`

## agent-harness-20261119-02

- 日期与上线状态判定：2026-11-19 Asia/Shanghai；新 starter 与旧模块均仅在 1.0.17-SNAPSHOT 工作区，最近发布标签 `v1.0.16` 不含旧模块；外部 SNAPSHOT 调用方未核实。
- 目标：吸收旧 Runtime 的按次工具源思路与 Core 的脱敏耗时观测，使用 AgentScope 2.0.5 原生类型；RAG 接入后移除旧 Core/Runtime 代码与 Maven 模块。
- 基线 Git ref：`20c4554ee53bc21c43c2cb5223084beb49b2b630` 加本轮前未提交新 starter；结果 Git ref：N/A。
- 当前入口：`AgentHarnessFactory#builder`、RAG `AgentScopeExecutor#execute`；替代目标：旧 `AgentRuntime#run/resume`、`AgentLoopKernel#run/resume` 的公共框架入口。
- 回滚目标：`agent-harness-20261119-01`；回滚来源：基线 Git ref 与本轮前新 starter 工作区；可用性：有条件，须同时恢复旧 POM 模块、源码及 RAG 依赖或保留新入口。
- 清理条件：RAG 已改用新工具源，当前框架内无旧模块外部源码引用；用户明确要求移除两个旧模块。其他未核实的外部 SNAPSHOT 消费者须随发布通知迁移。

| 文件 | 符号 | 职责 | 动作 | 状态 |
| --- | --- | --- | --- | --- |
| `kset-starter-agent-harness/src/main/java/com/kset/agent/harness/AgentHarnessFactory.java` | `builder` | 关闭原生内容追踪并登记耗时观测 | modify | active |
| `kset-starter-agent-harness/src/main/java/com/kset/agent/harness/AgentHarnessToolkitFactory.java` | `create` | 按次装配原生工具目录 | add | active |
| `kset-starter-agent-harness/src/main/java/com/kset/agent/harness/AgentHarnessToolSource.java` | `tools` | 原生工具源扩展点 | add | active |
| `kset-starter-agent-harness/src/main/java/com/kset/agent/harness/AgentHarnessTimingMiddleware.java` | `onAgent/onModelCall/onActing` | 脱敏耗时日志 | add | active |
| `kset-agent-core/`、`kset-agent-runtime/` | 旧循环、协议、Runtime | 移除不再接入的自研 Agent | remove | removed |
| `pom.xml`、`kset-boot-parent/pom.xml` | 模块与依赖管理 | 移除旧构件 | modify | active |

- 静态状态：两个旧模块 108 个跟踪文件在本轮前无工作区改动，`v1.0.16` 标签不含这两个模块；两仓 Java/POM/运行配置无旧模块引用，普通文档无指向已删源码的链接。POM XML 可解析，`git diff --check` 无空白错误。外部 SNAPSHOT 消费者未核实；未编译、测试、启动或部署。

## agent-harness-20261119-01

- 日期与上线状态判定：2026-11-19 Asia/Shanghai；新增模块未上线，当前工作区无该模块。
- 目标：在公共框架中接入 AgentScope Java 2.0.5 Harness，供业务按次构造 Agent；不迁移业务状态与 Web 契约。
- 基线 Git ref：`20c4554ee53bc21c43c2cb5223084beb49b2b630`；结果 Git ref：N/A。
- 回滚目标：基线无该模块；回滚来源：上述 Git ref 与本轮前既有未提交差异；可用性：有条件，须保留其他工作区改动。
- 替代关系：不替代 `kset-agent-core/runtime`，二者其他调用方未核实。
- 清理条件：使用方完成迁移且确认新模块构件可用后，才评估旧模块调用情况。

| 文件 | 符号 | 职责 | 动作 | 状态 |
| --- | --- | --- | --- | --- |
| `kset-starter-agent-harness/pom.xml` | Maven 依赖 | 管理 Harness 入口依赖 | add | active |
| `kset-starter-agent-harness/src/main/java/com/kset/agent/harness/AgentHarnessFactory.java` | `builder` | 按次构造原生 Builder | add | active |
| `kset-starter-agent-harness/src/main/java/com/kset/agent/harness/AgentHarnessProperties.java` | 配置 | 默认选项 | add | active |
| `kset-starter-agent-harness/src/main/java/com/kset/agent/harness/AgentHarnessAutoConfiguration.java` | 自动装配 | 条件化注册工厂 | add | active |
| `kset-boot-parent/pom.xml`、`pom.xml` | 依赖与聚合 | 固定版本、注册模块 | modify | active |

- 静态结果：按 AgentScope 2.0.5 源码核对 `HarnessAgent.Builder` 为公开类型，构造方法与现有 RAG 链式调用一致；父 POM、聚合 POM 与模块 POM 可被 XML 解析，自动装配 imports 指向现有类，`git diff --check` 通过。未编译、测试、启动或发布。

## 回滚步骤

1. 先确认业务调用方不再依赖新模块构件。
2. 移除新增模块和对应 POM、文档入口；保留基线已有 Core/Runtime 与无关工作区差异。
