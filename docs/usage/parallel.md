# 并行批跑（`Parallel`）

一批互相独立的任务同时执行，结果按入参列表顺序返回。依赖 `kset-common`（任意 Starter 都会带到）。

## 什么时候用

- 按 id 列表调下游，彼此没有先后依赖
- 需要限制同时在飞的任务数，避免打满业务线程池

不要用来做有依赖的编排，也不要在任务里再套一层无界并行。

## 怎么写

```java
import com.kset.common.utils.thread.Parallel;
import java.time.Duration;
import java.util.List;

List<User> users = Parallel.map("user-query", ids, userClient::findById);
List<User> limited = Parallel.map("user-query", ids, userClient::findById, 8, Duration.ofSeconds(2));

Parallel.run("order-notify", orders, this::notifyOne);
```

- 第一个参数是业务名，用来选 `KsetThreadPoolFactory` 里的池。
- 不传并发时最多 8 路。
- 会把当前线程的 `KsetContext`（登录用户、Trace）带到工作线程。

自带线程池时把 `Executor` 放第一个参数：

```java
Parallel.map(myExecutor, ids, userClient::findById, 4, Duration.ofSeconds(2));
```

## 失败与超时

- 已启动的任务会等到结束（或整体超时）；超时后取消未完成任务。
- 未完成项记为 `UncheckedTimeoutException`。
- `throwIfFailed` 按入参列表下标取第一条错误抛出，其余挂在 `getSuppressed()`。因此下标更靠前的若已业务失败，会先抛业务异常；若更靠前的是超时，则先抛 `UncheckedTimeoutException`。
- 中断抛 `UncheckedInterruptedException`。

```java
try {
    Parallel.map("user-query", ids, userClient::findById, 8, Duration.ofSeconds(2));
} catch (UncheckedTimeoutException timeout) {
    // 列表里第一条错误是超时
} catch (IllegalStateException first) {
    Throwable[] rest = first.getSuppressed();
}
```
