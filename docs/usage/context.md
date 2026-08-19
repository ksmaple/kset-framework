# 请求上下文（`KsetContext`）

依赖 `kset-common`。底层是 Alibaba `TransmittableThreadLocal`，在当前请求、线程池和 RPC 里带登录态、Trace、灰度、租户、语言。

Redis 只作为登录 session 存储，不是通用上下文底座。

## 怎么写

```java
KsetContext.put(KsetContextKeys.LOGIN_USER, loginUser);
KsetContext.put(KsetContextKeys.TRACE_ID, traceId);

LoginUser user = KsetContext.get(KsetContextKeys.LOGIN_USER).orElse(null);
KsetContextSnapshot snapshot = KsetContext.capture();

try (KsetContextScope ignored = KsetContext.openScope(snapshot)) {
    // async / rpc work
}
```

`LoginContext` 已委托到 `KsetContextKeys.LOGIN_USER`。业务继续用 `LoginContext.requireUser()` / `capture()` 即可。

`Parallel` 和本地 `EventFacade.publishAsync` / `publishDelay` 会自动带上调用线程的快照。

## 自定义 key

- 公共语义用 `KsetContextKeys`：`LOGIN_USER`、`TRACE_ID`、`TENANT_ID` 等。
- 业务 key 必须带命名空间，避免互相覆盖。
- 同名 key 如果类型、传播标记或敏感标记不同，注册会直接失败。
- `propagatable=false` 的 key 只在当前线程，不会进入 `capture()`。

```java
public static final KsetContextKey<String> ORDER_ID =
        KsetContextKey.of("order", "currentOrderId", String.class);

public static final KsetContextKey<String> LOCAL_TEMP =
        KsetContextKey.of("order", "localTemp", String.class, false, false);
```
