# 默认 profile 注入收窄 变更记录

- feature-key：`logging-default-profile`
- 当前 change-id：`logging-profile-narrow-v1`
- 当前状态：`active`
- 当前入口：`kset-common/src/main/java/com/kset/common/logging/autoconfigure/KsetLoggingEnvironmentPostProcessor.java#postProcessEnvironment`

> 2026-11-03 清理说明：`putDefaultProfileForRollback` 已移除，原行为从基线 Git ref 恢复；详见 `rollback-code-cleanup.md`。历史时点记录保留原样。

## 实现索引

| change-id | 状态 | 入口 | 替代目标 | 回滚来源 | 回滚可用性 | 清理条件 |
| --- | --- | --- | --- | --- | --- | --- |
| logging-profile-narrow-v1 | active | `KsetLoggingEnvironmentPostProcessor#postProcessEnvironment` | 原 spring.profiles.default 注入 | `KsetLoggingEnvironmentPostProcessor#putDefaultProfileForRollback` | 可用 | 不适用 |
| logging-profile-inject-baseline | rollback-candidate | `KsetLoggingEnvironmentPostProcessor#postProcessEnvironment` 原注入块 | logging-profile-narrow-v1 | `KsetLoggingEnvironmentPostProcessor#putDefaultProfileForRollback`、基线 Git ref `19f17ed` | 可用 | v1 稳定且无业务反馈后，经用户授权删除 ForRollback 符号 |

## logging-profile-narrow-v1

- 日期：2026-08-29
- 目标：不再向 Spring 环境注入 `spring.profiles.default=dev`，仅在日志层内部按 dev 判定行为，避免裸配应用静默落入 dev 语义
- 判定时间：2026-08-29 10:38:14 +08:00
- 上线状态：已上线（框架已发布 1.0.11 至内部 Nexus，存在业务消费方）
- 判定方式：自动判定
- 判定证据：pom.xml 版本 1.0.12-SNAPSHOT（1.0.11 已经 maven-release-plugin 发布）、git 历史含 release 提交、Nexus 发布配置
- 证据来源：`pom.xml`、`kset-boot-parent/pom.xml`、git log
- 当时代码状态：基线 Git ref `19f17ed`；`postProcessEnvironment` 在无 profile 时注入 `spring.profiles.default=dev`；`business-debug` 依赖该值判定
- 兼容选择：直接替换（用户确认选择"收窄"方案，接受裸配应用不再获得 dev profile 语义）
- 回滚目标：`logging-profile-inject-baseline`
- 基线 Git ref：`19f17ed6667a93bb12fadc949fcd52d8567dcb16`
- 结果 Git ref：N/A（未提交）
- 回滚来源：`kset-common/src/main/java/com/kset/common/logging/autoconfigure/KsetLoggingEnvironmentPostProcessor.java#putDefaultProfileForRollback`
- 回滚可用性：可用

| 文件 | 符号 | 职责 | 动作 | 状态 |
| --- | --- | --- | --- | --- |
| kset-common/.../logging/autoconfigure/KsetLoggingEnvironmentPostProcessor.java | postProcessEnvironment | 移除 spring.profiles.default 注入块 | modify | active |
| kset-common/.../logging/autoconfigure/KsetLoggingEnvironmentPostProcessor.java | putDefaultProfileForRollback | 原注入逻辑保留（未被生产路径调用） | retain | rollback-candidate |
| kset-common/.../logging/config/KsetLoggingProperties.java | defaultProfile / autoConfig 注释 | 语义改为仅日志层判定 | modify | active |
| kset-common/.../KsetLoggingEnvironmentPostProcessorTest.java | keepsDevLoggingBehaviorWithoutInjectingSpringDefaultProfile | 断言不再注入且 dev 日志行为保留（未执行） | modify | active |
| kset-common/README.md、additional-spring-configuration-metadata.json | - | 文档与元数据描述同步 | modify | active |

### 行为等价性说明

- 日志行为不变：未配 profile 时 Spring 隐式 `default` profile 匹配 `kset-logback-spring.xml` 的 `dev,default` 组，控制台+文件输出与之前一致。
- business-debug 判定不变：`isDevProfile` 在无 profile 时仍按 `kset.logging.default-profile`（默认 dev）判定开启。
- 唯一可观察差异：`environment.getProperty("spring.profiles.default")` 不再返回 dev（返回 null），依赖该属性显式读取的业务代码需注意；`spring.profiles.active` 两种实现下均为空，Nacos dataId 等基于 active profile 的解析不受影响。

### 回滚步骤

1. 在 `postProcessEnvironment` 中恢复调用 `putDefaultProfileForRollback(environment, defaults, defaultProfile)`。
2. 还原测试断言与文档描述，或按基线 Git ref `19f17ed6667a93bb12fadc949fcd52d8567dcb16` 恢复对应文件。
3. 更新本记录中的实现状态。
