# KSet MQ Starter

`kset-starter-mq` 提供 RocketMQ V5 Client Spring Boot Starter，并在存在 `RocketMQClientTemplate` 时注册 RocketMQ 版 `EventFacade`。

发布 / 消费写法见 [事件使用说明](../docs/usage/events.md)，Topic、Tag 与 Consumer Group 规划见 [RocketMQ 使用规范](../docs/usage/rocketmq.md)。不要在未引入本模块时注入 `RocketMqEventOperations`。

## 依赖

```xml
<dependency>
    <groupId>com.kset</groupId>
    <artifactId>kset-starter-mq</artifactId>
</dependency>
```

## 配置

```yaml
rocketmq:
  producer:
    endpoints: 127.0.0.1:8081
    topic: order-event
```

未配置事件 topic 时，`@KsetMqEvent(topic = "...")` 优先；注解 topic 为空时使用 `rocketmq.producer.topic`；仍为空时使用 `${spring.application.name:kset}-event`。

优先在相关事件共用的 Topic 下使用稳定 Tag。业务消费可继承 `RocketMqTagEventConsumer<T>`，在具体类上声明 `@RocketMQMessageListener` 的 Topic、Tag 与专属 Consumer Group。每个业务订阅独立确认；某个业务失败只使其 Group 重试，不会让其他 Group 中已成功的处理重复执行。

```java
@Component
@RocketMQMessageListener(
        endpoints = "${rocketmq.producer.endpoints:}",
        topic = "${rocketmq.producer.topic:order-event}",
        tag = "ORDER_CREATED",
        consumerGroup = "order-audit-consumer"
)
public class OrderAuditConsumer extends RocketMqTagEventConsumer<OrderCreatedEvent> {
    private final OrderAuditService auditService;

    public OrderAuditConsumer(ObjectMapper objectMapper, OrderAuditService auditService) {
        super(OrderCreatedEvent.class, objectMapper);
        this.auditService = auditService;
    }

    @Override
    protected void handle(OrderCreatedEvent event) {
        auditService.record(event);
    }
}
```

同一 Tag 若有另一个独立业务消费者，应另建监听器并使用不同 Group。具体类必须由 Spring 注册为 Bean；消费类不要同时实现 `EventHandler`。消息头的事件类型须与构造器指定类型一致，解析或业务处理失败返回 `FAILURE`，由该 Group 的 RocketMQ 重试和死信机制处理。业务处理仍须按稳定业务 ID 保证重复执行安全。

内置 `RocketMqEventConsumer` 默认继续订阅默认 Topic 的 `*` Tag，并按事件类型分发给所有 `EventHandler`。应用将 MQ 业务处理迁到独立监听器后，设置 `kset.mq.event.default-consumer-enabled=false` 关闭通配订阅，避免双重消费；该配置不影响事件发布和专属监听器。单个通配订阅内的多个 `EventHandler` 不具备业务间失败隔离。

通配消费者先判断消息类型是否有匹配的 `EventHandler`，只反序列化需要处理的事件；默认确认未匹配或本应用没有对应事件类的消息，以便共享 Topic 跳过无关事件。若该 Group 应处理订阅中的每一种事件，可设置 `kset.mq.event.fail-on-unhandled=true`，未匹配时返回 `FAILURE` 并进入该 Group 的重试/死信流程；共享 Topic 含其他业务消息时不要开启。`kset.event.enabled=false` 会关闭 KSet 自动装配的 MQ 发布门面与通配消费者，但不会关闭业务自行声明的 `@RocketMQMessageListener`。

发布业务只注入 `EventFacade`。需要编程式指定 topic/tag 时再注入 `RocketMqEventOperations`。`publish(topic, event)` 只覆盖 Topic，保留事件注解的 Tag；`publish(topic, tag, event)` 以显式 Tag 为准，传空 Tag 表示不附加 Tag。MQ 异步发送回调恢复调用时的 Trace，退出后还原客户端线程原有 Trace。

## 监控

引入 `kset-starter-monitor` 后，事件发布和消费路径会写入 `MonitorTypes.MQ` Transaction，并透传 traceId、spanId、grayTag。监控异常只记录日志，不影响业务发送和消费。
