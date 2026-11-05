# RocketMQ 使用规范

本规范用于规划 `kset-starter-mq` 的消息主题、标签和消费关系。分类原则参考阿里云的 [Topic 与 Tag 最佳实践](https://help.aliyun.com/zh/apsaramq-for-rocketmq/cloud-message-queue-rocketmq-4-x-series/use-cases/best-practices-of-topics-and-tags)；消费者分组约束参考[订阅关系一致](https://help.aliyun.com/zh/apsaramq-for-rocketmq/cloud-message-queue-rocketmq-4-x-series/use-cases/subscription-consistency)。两篇参考资料属于 RocketMQ 4.x 产品文档；本仓的具体发送与订阅行为以当前 V5 Starter 代码为准。

## 术语

| 术语 | 含义与依据 |
| --- | --- |
| Topic | 消息的一级分类，承载一组相关业务消息；阿里云 Topic 与 Tag 最佳实践。 |
| Tag | 生产者给消息设置的二级分类，用于区分同一 Topic 中的相关消息；阿里云 Topic 与 Tag 最佳实践。 |
| Consumer Group | 共同处理一份订阅的消费者实例组；同组实例须保持 Topic 和 Tag 过滤条件一致；阿里云订阅关系一致。 |
| 消息类型 | 普通、顺序、延时和 RocketMQ 事务等消息语义；阿里云 Topic 与 Tag 最佳实践要求不同类型分 Topic。 |

## Topic 与 Tag 规划

1. **优先用 Tag 区分相关事件。** 同一业务域、消息类型相同，且时效与流量规模相近的事件共用 Topic，每个稳定的事件类别使用一个 Tag。例如 `order-event` 下的 `ORDER_CREATED`、`ORDER_CANCELLED`。不要为每个普通事件类机械创建 Topic。
2. **需要独立语义或资源边界时拆 Topic。** 业务无直接关联、消息类型不同、优先级或时效要求不同、流量级差过大时使用不同 Topic。顺序消息和延时消息分别规划对应类型的 Topic，不能仅靠 Tag 与普通消息混用。
3. **Tag 表示类别，不表示消息实例。** Tag 使用稳定、可枚举的业务事件名，建议大写下划线；订单号、用户 ID、租户 ID、批次号等变化值放在消息体或业务标识中，不拼入 Tag。Tag 变更会影响订阅过滤，发送端与消费端应一起核对。
4. **先确定消费关系。** 同一 Consumer Group 的所有实例保持一致的 Topic 与 Tag 订阅；独立处理同一消息的业务使用不同 Group。不要复用一个 Group 承载过滤条件不同的消费者。

## 本仓接入方式

普通事件可以配置一个默认 Topic，并用注解声明 Tag：

```yaml
rocketmq:
  producer:
    endpoints: 127.0.0.1:8081
    topic: order-event
```

```java
@KsetMqEvent(tag = "ORDER_CREATED")
public record OrderCreatedEvent(String orderId) {}

@KsetMqEvent(tag = "ORDER_CANCELLED")
public record OrderCancelledEvent(String orderId) {}
```

- `EventFacade.publish(event)` 优先使用事件注解中的 Topic，否则使用 `rocketmq.producer.topic`，最后回退到 `{spring.application.name}-event`；注解中的 Tag 只影响发送目标。`RocketMqEventOperations.publish(topic, event)` 覆盖 Topic 并保留注解 Tag；三参数重载以显式 Tag 为准，空 Tag 表示不附加 Tag。
- 业务消费继承 `RocketMqTagEventConsumer<T>`，由具体 Spring Bean 上的 `@RocketMQMessageListener` 声明 Topic、稳定 Tag 与**业务专属 Consumer Group**；示例见 [Starter README](../../kset-starter-mq/README.md)。同一 Tag 可由不同业务 Group 各消费一份；Group 名须稳定，同组所有实例的 Topic 与 Tag 订阅保持一致。每个监听器只执行一个业务处理，不能在一次确认中串行调用多个互相独立的业务。
- 内置 `RocketMqEventConsumer` 默认仍订阅配置的默认 Topic、`*` Tag，Group 为 `${spring.application.name:kset}-event-consumer`，并按事件类型分发给所有 `EventHandler`。它不提供业务间失败隔离。完成迁移后配置 `kset.mq.event.default-consumer-enabled=false`，避免内置通配订阅与业务专属订阅重复处理；未迁移的 `EventHandler` 仍需要通配订阅或另行迁移。事件注解指定其他 Topic 后不会被内置消费者自动接收。
- 通配消费者先匹配处理器，再反序列化需要处理的事件；默认确认未匹配或本应用没有对应事件类的消息，适合共享 Topic 跳过无关事件。只有该 Group 应处理订阅内全部事件时才配置 `kset.mq.event.fail-on-unhandled=true`，使未匹配消息进入该 Group 的重试/死信流程。`kset.event.enabled=false` 关闭 KSet 自动装配的 MQ 发布门面与通配消费者；业务自行声明的 RocketMQ 监听器仍由 RocketMQ Starter 管理。
- `publishOrderly` 和 `publishDelay` 需要事先规划相应消息类型的 Topic。`publishTransaction` 在本仓表示 Spring 事务提交后发送普通消息，**不是** RocketMQ 事务消息；不能用它代替 RocketMQ 事务消息 Topic 与回查机制。
- 消费处理须按稳定业务标识做到重复执行安全；Tag 不承担幂等键职责。专属监听器的业务异常、类型不符和解析失败只使当前监听器返回 `FAILURE`，由其 Group 的重试与死信机制处理；其他 Group 的确认不受影响。不能通过吞异常或无条件返回成功实现隔离，也不能仅凭消息已投递就认定业务已生效。

项目中的 `grayTag` 是链路灰度标识，由消息属性透传，与 RocketMQ 的消息 Tag 不同。事件门面 API 和消费示例见[事件使用说明](events.md)；本机 Proxy 地址见[本地 Docker 公共组件](../../env/README.md)。
