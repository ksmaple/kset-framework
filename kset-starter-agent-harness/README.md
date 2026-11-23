# kset-starter-agent-harness

基于 AgentScope Java 2.0.5 的公共 Harness 装配入口。Spring Boot 引入模块后自动注册 `AgentHarnessFactory`，可通过 `kset.agent.harness.enabled=false` 关闭。

调用方每次运行提供 `AgentHarnessSpec`，从 `AgentHarnessFactory#builder` 获取独立的原生 `HarnessAgent.Builder`，继续配置执行超时、压缩、文件系统或 Middleware 等原生能力，再 `build()` 并在结束时关闭 Agent。需要全局默认选项时可注册线程安全的 `AgentHarnessCustomizer` Bean。

`AgentHarnessToolkitFactory#create` 接收本次 `RuntimeContext`、原生 `ToolkitConfig` 与 `AgentHarnessToolSource` 列表，固定本轮工具目录并拒绝空名、重名。`RuntimeContext.userId/sessionId` 必须由可信宿主提供；工具目录只控制可见性，工具执行仍需实时授权。

`Model`、`Toolkit`、`AgentStateStore`、运行身份和工作区均由调用方提供。Starter 默认关闭内置 Shell、文件、Web、记忆、子 Agent、Transcript 与会话持久化，以免绕过宿主授权与状态权威。还关闭 AgentScope 默认的 `AgentTraceMiddleware`（其 DEBUG 日志可含提示词、工具参数和结果），改用仅记录阶段、完成/失败/取消及耗时的 `AgentHarnessTimingMiddleware`。业务需自行实现会话存储、检查点、回滚和未知工具结果核验；`AgentStateStore` 本身不提供数据库事务或副作用回滚。

工厂使用 AgentScope 2.0.5 支持的最小正整数 `maxRetries(1)`；该版本拒绝零值。模型请求的尝试次数、超时与工具副作用核验仍由宿主明确管理，不能用零值配置重试边界。
