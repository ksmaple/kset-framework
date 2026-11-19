# 回滚旧代码清理记录

- feature-key：`rollback-code-cleanup`
- 当前 change-id：`rollback-code-cleanup-20261103-01`
- 当前状态：`active`
- 当前入口：各模块现有生产方法，均不调用本记录中的旧方法

## 实现索引

| change-id | 状态 | 入口 | 替代目标 | 回滚来源 | 回滚可用性 | 清理条件 |
| --- | --- | --- | --- | --- | --- | --- |
| rollback-code-cleanup-20261103-01 | active | 各模块现有生产入口 | 37 个回滚旧方法及 2 个闲置旧入口 | `5bc753897dc4c63f4c126d623cd07f9e46fbc88e` | 可用 | 不适用 |

## rollback-code-cleanup-20261103-01

- 日期：2026-11-03
- 目标：按项目规则从源码移除不承接调用的旧实现，以 Git ref 作为回滚来源。
- 判定时间：2026-11-03 18:05:32 +08:00
- 上线状态：不涉及兼容（私有方法无生产入口；两个公开 `destroyForRollback` 方法经用户确认无仓库外调用）
- 判定方式：源码引用核对及用户确认
- 判定证据：本仓无这些方法的生产调用；用户于 2026-11-03 确认两个公开方法无外部调用并同意删除。
- 证据来源：本仓源码搜索、当前对话、Git ref `5bc753897dc4c63f4c126d623cd07f9e46fbc88e`
- 当时代码状态：各模块生产入口均已有替代实现；仅旧方法及其自调用留在源码。受影响入口是下表所在类的现有方法；删除不改变入口调用关系。
- 兼容选择：N/A
- 回滚目标：N/A（仅移除无调用旧代码）
- 旧版本正式线上主分支及对比基线 Git SHA：待确认
- 旧版正式发布版本：待确认
- 基线 Git ref：`5bc753897dc4c63f4c126d623cd07f9e46fbc88e`
- 结果 Git ref：N/A（未提交）
- 回滚来源：Git ref `5bc753897dc4c63f4c126d623cd07f9e46fbc88e`；恢复时仅摘取所需符号，不覆盖本轮前已有工作区改动。
- 回滚可用性：可用

| 文件 | 符号 | 职责 | 动作 | 状态 |
| --- | --- | --- | --- | --- |
| `kset-agent-core/src/main/java/com/kset/agent/core/loop/AgentRunState.java` | `restoreForRollback/applyForRollback/stoppedForRollback` | 旧状态映射 | remove | removed |
| `kset-agent-core/src/main/java/com/kset/agent/core/strategy/ReactReasoningStrategy.java` | `modelVisibleStateForRollback` | 旧模型可见状态投影 | remove | removed |
| `kset-common/src/main/java/com/kset/common/event/spring/SpringEventFacade.java` | `withCapturedContextForRollback/publishDelayForRollback/publishOrderlyForRollback/destroyForRollback` | 旧事件上下文、发布和停机实现 | remove | removed |
| `kset-common/src/main/java/com/kset/common/logging/LogMaskingUtil.java` | `maskNodeForRollback/keyMatchesForRollback/maskEmailForRollback` | 旧脱敏实现 | remove | removed |
| `kset-common/src/main/java/com/kset/common/logging/autoconfigure/KsetLoggingEnvironmentPostProcessor.java` | `putDefaultProfileForRollback` | 旧默认 profile 注入 | remove | removed |
| `kset-common/src/main/java/com/kset/common/monitor/internal/DefaultMonitorFacade.java` | `putReactorContextForRollback` | 旧 Reactor 上下文转换 | remove | removed |
| `kset-common/src/main/java/com/kset/common/utils/date/DateHelper.java` | `formatterForRollback` | 旧日期格式化器 | remove | removed |
| `kset-common/src/main/java/com/kset/common/utils/http/HttpConvertUtils.java` | `convertMapToHttpGetParamsForRollback` | 旧参数编码 | remove | removed |
| `kset-common/src/main/java/com/kset/common/utils/http/KsetHttp.java` | `withTraceHeadersForRollback`、闲置旧构造器和转发方法 | 旧出站追踪实现 | remove | removed |
| `kset-common/src/main/java/com/kset/common/utils/retry/RetryBudget.java` | `ofForRollback/recordPrimaryForRollback/tryRetryForRollback/rotateWindowForRollback` | 旧预算创建与计数 | remove | removed |
| `kset-common/src/main/java/com/kset/common/utils/retry/RetryPolicy.java` | `shouldRetryForRollback` | 旧异常匹配 | remove | removed |
| `kset-common/src/main/java/com/kset/common/utils/retry/Retryer.java` | `asUncheckedForRollback` | 旧异常包装 | remove | removed |
| `kset-common/src/main/java/com/kset/common/utils/thread/BoundedPriorityBlockingQueue.java` | `offerForRollback/offerWaitForRollback` | 旧容量检查与等待 | remove | removed |
| `kset-common/src/main/java/com/kset/common/utils/thread/Parallel.java` | `awaitDoneForRollback/asUncheckedForRollback` | 旧并行等待与异常包装 | remove | removed |
| `kset-starter-auth/src/main/java/com/kset/auth/core/DefaultLoginUserHeaderCodec.java` | `decodeLegacyHeadersForRollback` | 旧头解码 | remove | removed |
| `kset-starter-gateway/src/main/java/com/kset/gateway/auth/HeaderTokenGatewayAuthProvider.java` | `authenticateForRollback` | 旧鉴权 | remove | removed |
| `kset-starter-gateway/src/main/java/com/kset/gateway/autoconfigure/KsetGatewayAutoConfiguration.java` | `gatewayErrorHandlerForRollback` | 旧错误处理装配 | remove | removed |
| `kset-starter-gateway/src/main/java/com/kset/gateway/filter/AuthGatewayFilter.java` | `filterForRollback` | 旧过滤链放行 | remove | removed |
| `kset-starter-gateway/src/main/java/com/kset/gateway/route/GatewayRouteRuleProvider.java` | `saveRouteForRollback` | 旧路由保存 | remove | removed |
| `kset-starter-redis/src/main/java/com/kset/redis/core/KsetRedisTemplateOperations.java` | `existsAllForRollback` | 旧批量存在查询 | remove | removed |
| `kset-starter-redis/src/main/java/com/kset/redis/lock/KsetRedisLockExecutor.java` | `resolveLeaseForRollback` | 旧租期解析 | remove | removed |
| `kset-starter-redis/src/main/java/com/kset/redis/lock/KsetRedisLockOptions.java` | `watchdogForRollback` | 旧 watchdog 判断 | remove | removed |
| `kset-starter-redis/src/main/java/com/kset/redis/lock/aop/KsetRedisLockAspect.java` | `toOptionsForRollback` | 旧注解选项解析 | remove | removed |
| `kset-starter-redis/src/main/java/com/kset/redis/lock/internal/KsetRedissonLockProvider.java` | `tryLockAllForRollback` | 旧批量锁获取 | remove | removed |
| `kset-starter-redis/src/main/java/com/kset/redis/support/KsetRedisNamedSources.java` | `destroyForRollback` | 旧连接关闭 | remove | removed |

### 回滚步骤

1. 选择需要恢复的功能变更记录与目标 change-id，按相应 Git ref 恢复必要入口和方法。
2. 若仅恢复清理的旧方法，从本记录基线摘取对应符号，保留其他工作区更改。
3. 按实际结果更新实现状态。
