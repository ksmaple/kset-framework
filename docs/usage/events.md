# 事件（`EventFacade`）

默认是进程内 Spring 事件。引入 `kset-starter-mq` 且存在 `RocketMQClientTemplate` 后，同一套 API 切到 RocketMQ。

发布业务依赖 `EventFacade` / `SendCallback`。进程内或内置 MQ 通配消费可使用 `EventHandler`；MQ 按 Tag 独立消费使用 `RocketMqTagEventConsumer`。编程式指定 topic/tag 时再注入 `RocketMqEventOperations`（需要 `kset-starter-mq`）。

## 发布

```java
@Service
public class OrderService {
    private final EventFacade events;

    public OrderService(EventFacade events) {
        this.events = events;
    }

    public void created(OrderCreatedEvent event) {
        events.publish(event);
    }
}
```

| 方法 | 行为 |
|------|------|
| `publish` | 当前线程同步投递 |
| `publishAsync` | 异步；本地实现会带上调用线程的登录态和 Trace |
| `publishDelay` | 延迟；本地失败打 error 并回调；MQ 在消息被接受时回调 |
| `publishOrderly` | 本地按 `hashKey` 分 64 槽串行；跨进程 FIFO 需要 MQ |
| `publishTransaction` | 有 Spring 事务时等提交后再发，没有则立刻发；不是 RocketMQ 事务消息 |

表中方法是不同发送方式，不应对同一次业务事件逐个调用。RocketMQ 的顺序、延时消息须使用对应消息类型的 Topic；见 [RocketMQ 使用规范](rocketmq.md)。事件对象不要为 `null`，延迟毫秒数不能为负。

## 消费

```java
@Component
public class OrderCreatedHandler implements EventHandler<OrderCreatedEvent> {
    @Override
    public Class<OrderCreatedEvent> eventType() {
        return OrderCreatedEvent.class;
    }

    @Override
    public void handle(OrderCreatedEvent event) {
        // 处理
    }
}
```

上例中的 `EventHandler` 由内置通配消费者调用，多个处理器共用一次消息确认。MQ 业务需要失败隔离时，继承 `RocketMqTagEventConsumer<T>` 并为每个业务声明专属 Consumer Group；接入示例见 [Starter README](../../kset-starter-mq/README.md)。Topic、Tag、Consumer Group 的判断规则见 [RocketMQ 使用规范](rocketmq.md)。

RocketMQ 配置见 [kset-starter-mq/README.md](../../kset-starter-mq/README.md)。
