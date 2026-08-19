# 重试（`Retryer`）

同步重试瞬时失败。第一次调用失败后才退避、占预算；业务失败和过载默认不重试。依赖 `kset-common`。

## 什么时候用

- 调 HTTP / RPC / Redis，偶发超时或连接失败
- 需要限制「重试次数 / 首次调用」比例，避免下游故障时本机把下游打满

不要对写操作无脑重试（要保证幂等）。`BusinessException` 表示业务规则失败，不会重试。

## 怎么写

```java
import com.kset.common.utils.retry.Retryer;
import com.kset.common.utils.retry.RetryPolicy;
import java.time.Duration;

String body = Retryer.call("order-http", () -> httpCall());

Order saved = Retryer.call(() -> orderClient.save(cmd),
        RetryPolicy.named("order-save")
                .maxElapsed(Duration.ofSeconds(3)));
```

生产请带业务名：`Retryer.call("业务名", ...)` 或 `RetryPolicy.named("业务名")`。未命名调用共用名为 `default` 的预算，一个故障接口会占光其他未命名重试。

## 默认策略

| 项 | 默认 |
|----|------|
| 最多尝试 | 3 次（1 次首次 + 最多 2 次重试） |
| 退避 | 指数 + 全抖动，100ms 起，上限 2s |
| 总时长 | 5 秒 |
| 预算 | 10 秒窗口内，重试不超过首次调用的 20%，窗口内至少 10 次 |
| 不重试 | `BusinessException`、中断、`IllegalArgumentException`、`NullPointerException`、线程池拒绝（沿 cause 链识别） |

## 预算

```java
RetryBudget.of("order-http");
RetryBudget.of("order-http", 0.1d, 5, Duration.ofSeconds(10));
```

同一业务名多次 `of` 时比例、窗口必须一致，否则抛 `IllegalStateException`。全进程共用一份额度用 `RetryBudget.shared()`；单测或明确允许打满用 `policy.noBudget()`。

## 失败形态

- 超时抛 `UncheckedTimeoutException`
- 中断抛 `UncheckedInterruptedException`
- 其他检查异常包成 `RuntimeException`
