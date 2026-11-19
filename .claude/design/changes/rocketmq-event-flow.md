# RocketMQ 事件流程变更记录

- feature-key：`rocketmq-event-flow`
- 当前 change-id：`rocketmq-event-flow-v2`
- 当前状态：`active`
- 当前入口：`kset-starter-mq/src/main/java/com/kset/mq/event/RocketMqEventFacade.java#publish`、`publishAsync`；`kset-starter-mq/src/main/java/com/kset/mq/event/RocketMqEventConsumer.java#consume`

## 实现索引

| change-id | 状态 | 入口 | 替代目标 | 回滚来源 | 回滚可用性 | 清理条件 |
| --- | --- | --- | --- | --- | --- | --- |
| rocketmq-event-flow-v2 | active | MQ 发布与通配消费入口 | N/A，兼容扩展 | `a07d3f67ec1e63ae6ee83dae7c6abbc42ee1a91c` 及本轮修改前工作树 | 部分可用 | N/A |
| rocketmq-event-flow-baseline | active-compatible | 原有 `publish(topic, event)`、通配消费默认确认路径 | N/A | `a07d3f67ec1e63ae6ee83dae7c6abbc42ee1a91c` | 部分可用 | 调用方完成语义迁移且另行确认 |

## rocketmq-event-flow-v2

- 日期：2026-11-05
- 目标：统一 Tag 路由、开关与异步 Trace；通配消费先匹配再反序列化，并为未匹配事件提供可选严格处理。
- 判定时间：2026-11-05 11:52:44 +08:00
- 上线状态：待确认
- 判定方式：现有标签与代码核对，缺少目标环境部署映射
- 判定证据：`v1.0.16` 标签含 MQ 发布实现；本轮 Tag 专属消费仍为未提交工作树改动
- 证据来源：Git `v1.0.16`、`a07d3f67ec1e63ae6ee83dae7c6abbc42ee1a91c`
- 当时代码状态：`RocketMqEventFacade#publish(String,Object)` 丢弃注解 Tag；`RocketMqEventConsumer#consume` 未匹配时确认成功；MQ 自动配置不受 `kset.event.enabled` 控制；异步完成回调不恢复提交线程 Trace。仓内未找到该重载的业务调用方；外部调用方迁移状态未知。
- 兼容选择：保留旧签名及通配消费默认确认行为；Topic-only 发布保留事件注解 Tag；默认模式对外部未知类消息也按无关事件确认
- 回滚目标：`rocketmq-event-flow-baseline`
- 旧版本正式线上主分支及对比基线 Git SHA：待确认
- 旧版正式发布版本（仅主分支开发项目）：待确认
- 基线 Git ref：`a07d3f67ec1e63ae6ee83dae7c6abbc42ee1a91c`
- 结果 Git ref：N/A（未提交）
- 回滚来源：上述 Git ref 加本轮修改前工作树内容
- 回滚可用性：部分可用；本轮未提交文件不能仅靠 Git ref 恢复

| 文件 | 符号 | 职责 | 动作 | 状态 |
| --- | --- | --- | --- | --- |
| `kset-starter-mq/src/main/java/com/kset/mq/event/RocketMqEventFacade.java` | `publish`、`publishAsync`、`handleAsyncResult` | 发送目标及异步 Trace | modify | active |
| `kset-starter-mq/src/main/java/com/kset/mq/event/RocketMqEventDestinationResolver.java` | `destination` | Topic 与 Tag 解析 | modify | active |
| `kset-starter-mq/src/main/java/com/kset/mq/event/RocketMqEventOperations.java` | `publish` 注释 | 公开发送契约 | modify | active |
| `kset-starter-mq/src/main/java/com/kset/mq/event/RocketMqEventConsumer.java` | `consume`、`dispatch` | 通配确认 | modify | active-compatible |
| `kset-starter-mq/src/main/java/com/kset/mq/autoconfigure/KsetRocketMqEventAutoConfiguration.java` | 自动配置条件 | 事件开关和严格模式 | modify | active-compatible |
| `kset-starter-mq/src/test/java/com/kset/mq/event/KsetRocketMqEventFacadeTest.java` | 聚焦用例 | 契约检查 | modify | active |
| `kset-starter-mq/README.md`、`docs/usage/rocketmq.md` | 接入说明 | 配置与行为约定 | modify | active |
| `.claude/skills/kset-framework-coder/references/project-spec.md` | RocketMQ 约定 | 项目事实 | modify | active |

### 回滚步骤

1. 从基线 Git ref 和本轮修改前工作树内容恢复 MQ 发布、通配消费与自动配置入口。
2. 同步恢复严格模式配置、接入文档及调用方的 Topic/Tag 使用方式。
3. 核对业务监听器与 Group 订阅关系后更新本记录状态。
