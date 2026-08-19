# 事件（`EventFacade`）

默认是进程内 Spring 事件。引入 `kset-starter-mq` 且存在 `RocketMQClientTemplate` 后，同一套 API 切到 RocketMQ。

业务只依赖 `EventFacade` / `EventHandler` / `SendCallback`。编程式指定 topic/tag 时再注入 `RocketMqEventOperations`（需要 `kset-starter-mq`）。

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
        events.publishAsync(event, null);
        events.publishDelay(event, 30_000L, new SendCallback() {
            @Override
            public void onSuccess() {
            }

            @Override
            public void onException(Throwable error) {
            }
        });
        events.publishOrderly(event, String.valueOf(event.userId()));
        events.publishTransaction(event);
    }
}
```

| 方法 | 行为 |
|------|------|
| `publish` | 当前线程同步投递 |
| `publishAsync` | 异步；本地实现会带上调用线程的登录态和 Trace |
| `publishDelay` | 延迟；本地失败打 error 并回调；MQ 在消息被接受时回调 |
| `publishOrderly` | 本地按 `hashKey` 分 64 槽串行；跨进程 FIFO 需要 MQ |
| `publishTransaction` | 有 Spring 事务时等提交后再发，没有则立刻发 |

事件对象不要为 `null`。延迟毫秒数不能为负。

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

MQ 侧建议给事件类加 `@KsetMqEvent(topic = "...", tag = "...")`。topic 为空时用 `rocketmq.producer.topic`，再空则 `{spring.application.name}-event`。

RocketMQ 配置见 [kset-starter-mq/README.md](../../kset-starter-mq/README.md)。
