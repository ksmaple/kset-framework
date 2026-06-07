# KSet MQ Starter

`kset-starter-mq` 提供 RocketMQ V5 Client Spring Boot Starter，并在存在 `RocketMQClientTemplate` 时注册 RocketMQ 版 `EventFacade`。

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

## 事件发布

```java
@KsetMqEvent(topic = "order-event", tag = "created")
public record OrderCreatedEvent(Long orderId, Long userId) {
}

@Service
public class OrderService {
    private final EventFacade eventFacade;
    private final RocketMqEventOperations rocketMqEvents;

    public OrderService(EventFacade eventFacade, RocketMqEventOperations rocketMqEvents) {
        this.eventFacade = eventFacade;
        this.rocketMqEvents = rocketMqEvents;
    }

    public void publish(Long orderId, Long userId) {
        OrderCreatedEvent event = new OrderCreatedEvent(orderId, userId);
        eventFacade.publish(event);
        eventFacade.publishAsync(event, null);
        eventFacade.publishDelay(event, 30 * 60 * 1000L);
        eventFacade.publishOrderly(event, String.valueOf(userId));

        rocketMqEvents.publish("order-event", "created", event);
    }
}
```

## 消费

业务实现 `EventHandler<T>` 后，RocketMQ consumer 会按事件类型分发：

```java
@Component
public class OrderCreatedHandler implements EventHandler<OrderCreatedEvent> {
    @Override
    public Class<OrderCreatedEvent> eventType() {
        return OrderCreatedEvent.class;
    }

    @Override
    public void handle(OrderCreatedEvent event) {
        // 处理事件
    }
}
```

## 监控

引入 `kset-starter-monitor` 后，事件发布和消费路径会写入 `MonitorTypes.MQ` Transaction，并透传 traceId、spanId、grayTag。监控异常只记录日志，不影响业务发送和消费。
