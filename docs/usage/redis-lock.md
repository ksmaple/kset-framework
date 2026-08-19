# Redis 分布式锁

依赖 `kset-starter-redis`。锁只走 Redisson，连主 Redis（`spring.data.redis.*`）。无需锁时 `kset.redis.redisson.enabled=false`。

## 推荐入口

优先注入 `KsetRedisLockExecutor`。静态 `KsetRedisLocks` 仅在尚未注入的老代码里用。

```java
@Service
public class OrderSyncService {
    private final KsetRedisLockExecutor locks;

    public OrderSyncService(KsetRedisLockExecutor locks) {
        this.locks = locks;
    }

    public void sync(String orderId) {
        locks.runExclusive("order:sync:" + orderId, () -> doSync(orderId));
    }
}
```

方法级：

```java
@KsetLocked("'order:sync:' + #orderId")
public void sync(String orderId) {
    doSync(orderId);
}
```

同类内部 `this.sync()` 不会走 AOP，请注入自身或改用 `KsetRedisLockExecutor`。

## 抢锁策略

| 方法 | 抢不到时 |
|------|----------|
| `runExclusive` / `rejectNow` | 立刻失败，`KsetRedisLockBusyException` |
| `runWithWait` / `waitThenFail` | 等到 `waitTime`，超时 `KsetRedisLockTimeoutException` |
| `runBlocking` / `blockUntil` | 一直等到拿到 |
| `callIfLock` / `OPTIONAL` | 拿不到则跳过，返回 `null` / `Optional.empty()` |

等待期间线程被中断抛 `KsetRedisLockInterruptedException`，不要当成没抢到。同时引入 `kset-starter-web` 时，繁忙 / 超时 / 中断映射业务码 409 / 408 / 503，响应不含 lockKey。

## 租约与 watchdog

默认租约 **30 秒**，固定 lease 时 Redisson **不会**自动续期。临界区可能超过 30 秒时：

- 显式加长 `lease`，或
- 使用 watchdog：`rejectNowWatchdog()` / `waitThenFailWatchdog(wait)` / `blockUntilWatchdog()` / `@KsetLocked(lease = "0s")`

```java
locks.run("job", KsetRedisLockOptions.rejectNowWatchdog(), this::longJob);
```

`rejectNow(Duration.ZERO)` 和 `builder().leaseTime(Duration.ZERO)` **不会**开 watchdog，会按 TTL 策略拒绝。不要把 `0` 当占位。

Watchdog 会续到 `unlock`。临界区挂死且进程还活着时，锁不会自己掉。进程崩溃后约 30 秒释放。

## Key

锁 key 用 `KsetRedisKeys.lock(system, lockName)`，两段都不能空，段内不要包含 `:`：

```java
String key = KsetRedisKeys.lock("myapp", "order-sync-" + orderId);
// myapp:lock:order-sync-1001
```

也可以自己拼业务前缀，但段内同样不要带 `:`。
