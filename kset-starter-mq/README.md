# KSet MQ Starter

`kset-starter-mq` 提供 RocketMQ V5 Client Spring Boot Starter，并在存在 `RocketMQClientTemplate` 时注册 RocketMQ 版 `EventFacade`。

发布 / 消费写法见 [事件使用说明](../docs/usage/events.md)。不要在未引入本模块时注入 `RocketMqEventOperations`。

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

业务只注入 `EventFacade`。需要编程式指定 topic/tag 时再注入 `RocketMqEventOperations`。

## 监控

引入 `kset-starter-monitor` 后，事件发布和消费路径会写入 `MonitorTypes.MQ` Transaction，并透传 traceId、spanId、grayTag。监控异常只记录日志，不影响业务发送和消费。
