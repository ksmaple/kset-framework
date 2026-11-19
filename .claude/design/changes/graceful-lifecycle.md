# 统一优雅启停 变更记录

- feature-key：`graceful-lifecycle`
- 当前 change-id：`graceful-lifecycle-v1`
- 当前状态：`active`
- 当前入口：`kset-common/src/main/java/com/kset/common/lifecycle/autoconfigure/KsetLifecycleAutoConfiguration.java#KsetLifecycleAutoConfiguration`

> 2026-11-03 清理说明：`SpringEventFacade#destroyForRollback` 与 `KsetRedisNamedSources#destroyForRollback` 已在用户确认无外部调用后移除；原行为从基线 Git ref 恢复，详见 `rollback-code-cleanup.md`。以下历史时点记录保留原样。

## 实现索引

| change-id | 状态 | 入口 | 替代目标 | 回滚来源 | 回滚可用性 | 清理条件 |
| --- | --- | --- | --- | --- | --- | --- |
| graceful-lifecycle-v1 | active | `KsetLifecycleAutoConfiguration` 及各 starter `Kset*Lifecycle` Bean | 各组件原 DisposableBean 硬关实现 | `SpringEventFacade#destroyForRollback`、`KsetRedisNamedSources#destroyForRollback`、基线 Git ref `19f17ed` | 可用 | 不适用 |
| pre-graceful-baseline | rollback-candidate | `SpringEventFacade#destroy`、`KsetRedisNamedSources#destroy`、`KsetThreadPoolFactory#shutdownAll` 原实现 | graceful-lifecycle-v1 | Git ref `19f17ed6667a93bb12fadc949fcd52d8567dcb16` + 上述 ForRollback 符号 | 可用 | v1 稳定且无回滚诉求后，经用户授权删除 ForRollback 符号 |

## graceful-lifecycle-v1

- 日期：2026-08-29
- 目标：全部 kset 组件统一接入基于 Spring SmartLifecycle 相位的启停编排，停机"排空 + 超时兜底"，并默认开启 Web/Dubbo 优雅停机
- 判定时间：2026-08-29 10:38:14 +08:00
- 上线状态：不涉及兼容（新增能力；对外 API 无变化，仅停机行为由硬关升级为优雅关）
- 判定方式：自动判定
- 判定证据：无发布/部署记录可定位该能力；行为变更仅限进程停机路径
- 证据来源：N/A
- 当时代码状态：基线 Git ref `19f17ed`；`SpringEventFacade#destroy`、`KsetRedisNamedSources#destroy` 为 shutdownNow/直接关闭硬停机；`KsetThreadPoolFactory#shutdownAll` 无排空等待；无 `server.shutdown`/`spring.lifecycle`/Dubbo shutwait 默认值
- 兼容选择：N/A
- 回滚目标：`pre-graceful-baseline`
- 基线 Git ref：`19f17ed6667a93bb12fadc949fcd52d8567dcb16`
- 结果 Git ref：N/A（未提交）
- 回滚来源：`kset-common/src/main/java/com/kset/common/event/spring/SpringEventFacade.java#destroyForRollback`、`kset-starter-redis/src/main/java/com/kset/redis/support/KsetRedisNamedSources.java#destroyForRollback`、基线 Git ref
- 回滚可用性：可用

| 文件 | 符号 | 职责 | 动作 | 状态 |
| --- | --- | --- | --- | --- |
| kset-common/.../lifecycle/KsetLifecyclePhases.java | KsetLifecyclePhases | 启停相位常量（100~500） | add | active |
| kset-common/.../lifecycle/AbstractKsetLifecycleComponent.java | AbstractKsetLifecycleComponent | SmartLifecycle 基类，幂等 shutdownLifecycle | add | active |
| kset-common/.../lifecycle/KsetLifecycleProperties.java | KsetLifecycleProperties | `kset.lifecycle.*` 配置 | add | active |
| kset-common/.../lifecycle/KsetGracefulShutdowns.java | KsetGracefulShutdowns | 执行器排空+超时兜底工具（含强关后 2s 清理等待） | add | active |
| kset-common/.../lifecycle/KsetThreadPoolLifecycle.java | KsetThreadPoolLifecycle | 业务线程池统一停机（PHASE_THREAD_POOL） | add | active |
| kset-common/.../lifecycle/KsetReadinessLifecycle.java | KsetReadinessLifecycle | 停机最先置 readiness=REFUSING_TRAFFIC（PHASE_READINESS） | add | active |
| kset-common/.../lifecycle/KsetLifecycleHealthIndicator.java | KsetLifecycleHealthIndicator | /actuator/health 暴露各组件排空进度（可选 actuator） | add | active |
| kset-common/.../lifecycle/KsetLifecycleHook.java | KsetLifecycleHook | 业务侧接入统一启停的扩展契约 | add | active |
| kset-common/.../lifecycle/KsetLifecycleHookAdapter.java | KsetLifecycleHookAdapter | Hook 的 SmartLifecycle 适配器（函数式/接口式注册） | add | active |
| kset-common/.../lifecycle/autoconfigure/KsetLifecycleAutoConfiguration.java | KsetLifecycleAutoConfiguration | 统一启停装配入口 | add | active |
| kset-common/.../lifecycle/autoconfigure/KsetLifecycleEnvironmentPostProcessor.java | KsetLifecycleEnvironmentPostProcessor | server.shutdown=graceful 等默认值注入 | add | active |
| kset-common/.../utils/thread/KsetThreadPoolFactory.java | shutdownAll(long) | 全池优雅关闭；shutdownAll() 委托新方法（默认 30s） | modify | active |
| kset-common/.../event/spring/SpringEventFacade.java | doStop / destroy / destroyForRollback | 事件执行器优雅排空（PHASE_EVENT） | modify | active |
| kset-common/.../event/spring/SpringEventFacade.java | destroyForRollback | 原硬关实现保留 | retain | rollback-candidate |
| kset-common/.../event/autoconfigure/KsetEventAutoConfiguration.java | eventFacade | 注入统一停机超时 | modify | active |
| kset-starter-redis/.../support/KsetRedisNamedSources.java | doStop / destroy / destroyForRollback | 命名数据源连接 PHASE_INFRA 最后关闭 | modify | active |
| kset-starter-mq/.../lifecycle/KsetMqEventLifecycle.java | KsetMqEventLifecycle | RocketMQ 监听容器停收（PHASE_TRAFFIC） | add | active |
| kset-starter-mq/.../autoconfigure/KsetRocketMqEventAutoConfiguration.java | ksetMqEventLifecycle | MQ 生命周期 Bean 注册 | modify | active |
| kset-starter-monitor/.../lifecycle/KsetMonitorLifecycle.java | KsetMonitorLifecycle | 监控 flush + 关闭异步上报（PHASE_MONITOR） | add | active |
| kset-starter-monitor/.../autoconfigure/KsetMonitorFacadeAutoConfiguration.java | ksetMonitorLifecycle | 监控生命周期 Bean 注册 | modify | active |
| kset-starter-dubbo/.../KsetDubboEnvironmentPostProcessor.java | postProcessEnvironment | dubbo.application.shutwait=10000 默认值 | modify | active |
| kset-starter-dubbo/.../lifecycle/KsetDubboLifecycle.java | KsetDubboLifecycle | Dubbo `DubboBootstrap.stop()` 摘流停收（PHASE_TRAFFIC） | add | active |
| kset-starter-dubbo/.../autoconfigure/KsetDubboGovernanceAutoConfiguration.java | ksetDubboLifecycle | Dubbo 生命周期 Bean 注册 | modify | active |
| kset-common resources（AutoConfiguration.imports / EnvironmentPostProcessor / additional-spring-configuration-metadata.json） | - | 自动装配与配置元数据注册 | modify | active |
| 各模块 *Test.java（lifecycle/factory/facade/named-sources） | - | 优雅停机单测（按项目规则未执行） | add | active |

### 回滚步骤

1. 业务侧配置 `kset.lifecycle.enabled=false` 关闭统一编排；显式配置 `server.shutdown=immediate` 覆盖默认值。
2. 如需恢复硬停机实现：将 `SpringEventFacade#destroy`、`KsetRedisNamedSources#destroy` 改回调用各自 `destroyForRollback` 逻辑，或按基线 Git ref `19f17ed6667a93bb12fadc949fcd52d8567dcb16` 恢复对应文件。
3. 更新本记录中的实现状态。
