# kset-common 工具类

`kset-common` 模块提供与业务无关的通用能力，包根路径为 `com.kset.common`。以下工具由 bobo-common-utils 迁移或在本仓新增，**日期时间统一使用 JDK `java.time`**（不依赖 Joda-Time）。

## ListHelper（`com.kset.common.utils.collection`）

列表分批、函数式转换、排序与空安全取用。日常单元素变换用 JDK Stream；IN 上限、Redis 分片、批量写库等用分批 API。

| 场景 | 推荐 |
|------|------|
| `map` / `filter` / 复杂链式 | JDK Stream |
| 分批查库 / RPC / Redis | `ListHelper.batchMap` / `forEachBatch` |
| 按字段排序 | `ListHelper.sortBy` / `sortByDesc` |
| 三方库 | Guava `Lists.partition` 可用，业务统一走 `ListHelper` |

```java
import com.kset.common.utils.collection.ListHelper;

// 500 条一批查用户，结果顺序与 ids 一致
List<User> users = ListHelper.batchMap(ids, 500, userRepo::findByIds);

// 分批写 Redis
ListHelper.forEachBatch(keys, 200, batch -> redisTemplate.opsForValue().multiSet(...));

// 分组 / 去重 / 转 Map
Map<String, List<Order>> byStatus = ListHelper.groupBy(orders, Order::getStatus);
List<Order> unique = ListHelper.distinctBy(orders, Order::getOrderNo);

// 排序（拷贝后排序，null key 升序时排最后）
List<Order> sorted = ListHelper.sortByDesc(orders, Order::getCreateTime);

// 空安全
if (ListHelper.isNotEmpty(list)) { ... }
```

## DateHelper（`com.kset.common.utils.date`）

链式日期时间 API，内部为服务器本地 `LocalDateTime`（不做跨时区转换）。

```java
import com.kset.common.utils.date.DateHelper;

// 智能解析（自动识别常见格式）
DateHelper.parse("2024-06-07 14:05:30");
DateHelper.parse("2024-06-07");
DateHelper.parse("20240607");
DateHelper.parse("202406");
DateHelper.parse("2024");
DateHelper.of(existingDate);

// 链式调整
DateHelper.parse("2024-06-07").withTime("15:30:00").addDay(1);

// 与 java.time 互转
DateHelper.parse("2024-06-07").toLocalDateTime();
DateHelper.parse("2024-06-07").toLocalDate();

// ── 左闭右闭 [start, end] ──
DateHelper.parse("2024-01-01 12:00:00").isRange(start, end);
DateHelper.rangeInclusive(start, end);
DateHelper.thisMonthRangeInclusive();

// ── 左闭右开 [start, end)（SQL / 统计）──
DateHelper.isInRangeExclusive(point, start, endExclusive);
DateHelper.build().isRangeExclusive(start, endExclusive);
DateHelper.rangeExclusive(start, endExclusive);
DateHelper.thisMonthRange();
DateHelper.DatePeriod month = DateHelper.build().monthRangeExclusive();
```

| 迁移说明 | 原 Joda API | 现 API |
|----------|-------------|--------|
| 时区构造 | `build(DateTimeZone)` | 见 `DateZoneHelper` |
| 星期参数 | ISO 1=周一 … 7=周日 | 不变 |

常用模式常量：`PATTERN_DEF` 等见类定义。

## DateZone / DateZoneHelper（`com.kset.common.utils.date`）

时区转换，支持人类习惯输入：整小时偏移、GMT/UTC 字符串、内置 {@link DateZone} 枚举。

```java
import com.kset.common.utils.date.DateZone;
import com.kset.common.utils.date.DateZoneHelper;

// 整小时偏移：8 → 东八区，-5 → 西五区
DateZoneHelper.zoneOf(8);
DateZoneHelper.parseZone("GMT+8");
DateZoneHelper.parseZone("UTC+8");
DateZoneHelper.parseZone("+8");
DateZoneHelper.parseZone("CN");

// 内置常见时区
DateZone.CN.toZoneId();       // GMT+8
DateZone.SAU.toGmtLabel();     // GMT+3
DateZone.IN.toZoneId();        // GMT+05:30

// 转换
DateZoneHelper.of(epochMillis, 8).toZone(DateZone.SAU).format(DateHelper.PATTERN_DEF);
DateZoneHelper.format(date, "GMT+8", DateHelper.PATTERN_DEF);

// 墙钟 ⇄ 本地（同一时刻）
DateHelper local = DateZoneHelper.wallClockToLocal(sauWall, DateZone.SAU);
LocalDateTime cn = DateZoneHelper.localToWallClock(local, DateZone.CN);
DateZoneHelper.sauToLocalDef("2024-06-07 10:00:00");  // 沙特快捷
```

内置枚举：`UTC`、`CN`、`SAU`、`JP`、`SG`、`UAE`、`IN`、`UK`、`US_EAST`、`US_WEST`（固定偏移，不含夏令时）。

## VersionUtil（`com.kset.common.utils`）

常见版本号比较与判断。数字段逐段比较，缺失段按 0 处理，因此 `1.0`、`1.0.0`、`1.0.0.0` 视为相同版本；支持 `v` 前缀、`-SNAPSHOT`、`-RC1`、`.Final` 等常见后缀。

```java
import com.kset.common.utils.VersionUtil;

VersionUtil.isEqual("1.0", "1.0.0.0");          // true
VersionUtil.greaterThan("1.0.1", "1.0.0.9");   // true
VersionUtil.isAtLeast("1.5.0", "1.4.9");       // true
VersionUtil.inRange("1.5.0", "1.0.0", "2.0.0");
```

## KsetHttp（`com.kset.common.utils.http`）

基于 OkHttp 的 HTTP 客户端封装（原 `DDKJHttp`）。

```java
import com.kset.common.utils.http.KsetHttp;

String body = KsetHttp.get("https://example.com/api")
        .header("X-Token", token)
        .executeString();
```

配套：`HttpConvertUtils`、`HttpLogInterceptor`（SLF4J）、`RetryInterceptor`。

## 线程池（`com.kset.common.utils.thread`）

按业务名隔离、支持指标与 MDC 链路传递（原 `DDKJThreadPool*`）。

```java
import com.kset.common.utils.thread.KsetThreadPoolFactory;
import com.kset.common.utils.thread.MdcThreadPoolTraceAdapter;
import com.kset.common.monitor.Monitor;

KsetThreadPoolFactory factory = KsetThreadPoolFactory.getInstance();
factory.setGlobalTraceContextAdapter(new MdcThreadPoolTraceAdapter());
factory.register("order-payment", KsetThreadPoolFactory.PoolConfig.ioConfig());
factory.execute("order-payment", () -> {
    // 子线程可读取 Monitor.currentTraceId()
    callExternalApi();
});
```

| 类 | 说明 |
|----|------|
| `KsetThreadPoolFactory` | 推荐入口，按 biz 名懒创建池 |
| `KsetThreadPoolExecutor` | 底层实现，可 Builder 单独使用 |
| `ThreadPoolMetrics` / `ThreadPoolReporter` | 指标与上报 |
| `ThreadPoolTraceAdapter` / `MdcThreadPoolTraceAdapter` | TraceId 跨线程传递（基于 `com.kset.common.monitor.Monitor`） |

## 日志与链路上下文（`com.kset.common.logging` + `com.kset.common.monitor`）

**分布式 traceId** 由 `Monitor` 写入 MDC（HTTP/Dubbo/Gateway Filter 或 `Monitor.bindHttpIncoming`），logging 包只消费，不重复存储。

| 类 | 说明 |
|----|------|
| `StructLog` / `LogUtil` | 结构化日志；traceId 随 MDC 自动进入 JSON |
| `FlowLogContext` | 业务流程步骤（`flow.*` MDC 键），与 traceId 共存 |
| `OpLogContext` | 操作人（MDC `operator` 键） |
| `LogMaskingUtil` | 敏感字段脱敏 |

```java
import com.kset.common.logging.FlowLogContext;
import com.kset.common.logging.FlowLogContext.FlowEventType;
import com.kset.common.logging.StructLog;
import com.kset.common.monitor.Monitor;
import com.kset.common.monitor.MonitorScope;

private static final StructLog LOG = StructLog.of(OrderService.class);

public void process(String userId) {
    // traceId 通常已由 Filter 绑定；结构化日志自动带 traceId
    LOG.info("start process", "userId", userId);

    String flowId = FlowLogContext.beginFlow("order_create", userId);
    try {
        FlowLogContext.step("validate", FlowEventType.ENTER);
        // ...
        FlowLogContext.endFlow();
    } finally {
        FlowLogContext.clear(); // 不清 Monitor
    }
}

// 跨线程：Monitor 链路 + flow MDC 一并传播
executor.execute(() -> {
    try (MonitorScope scope = Monitor.openScope(Monitor.capture())) {
        FlowLogContext.step("async_step", FlowEventType.ENTER);
    }
});
```

详见 [kset-starter-monitor/README.md](../kset-starter-monitor/README.md)「与 logging 协作」小节。

## 随机（`com.kset.common.utils.random`）

### 基础 API（兼容旧版）

| 类 | 说明 |
|----|------|
| `RandomUtils` | `ThreadLocalRandom` 封装 |
| `RandomBox` | 加权轮盘，支持 `hidden(key)` |
| `SeededRandomBox` | 固定种子 + 整数配额（极小概率） |

### 推荐：Engine + Registry

| 类 | 说明 |
|----|------|
| `WeightedRandomEngine` | 单池引擎：可选加权、指标、journal、种子重放 |
| `KsetRandomRegistry` | 多业务注册中心（对标 `KsetThreadPoolFactory`） |
| `WeightedRandomMetrics` | 抽取分布快照（`toJson()`） |
| `DrawEvent` / `WeightedRandomReplayer` | 日志重放（审计/纠纷） |

```java
import com.kset.common.utils.random.*;

// 单引擎
WeightedRandomEngine engine = WeightedRandomEngine.builder()
        .name("lottery")
        .weights(Map.of("common", 0.9d, "rare", 0.1d))
        .metricsEnabled(true)
        .journalEnabled(true)
        .replayEnabled(true)
        .seed(42L)
        .replayStore(myReplayStore)
        .persistence(myPersistence)
        .build();

String prize = engine.draw("sold-out");
WeightedRandomMetrics metrics = engine.getMetrics();
engine.flush();

// 种子预览（需 replayEnabled + seed）
List<String> nextFive = engine.previewSequence(5);

// 多业务
KsetRandomRegistry reg = KsetRandomRegistry.getInstance();
reg.setGlobalPersistence(persistence);
reg.setGlobalReplayStore(replayStore);
reg.register("gacha", WeightedRandomConfig.builder().weights(weights).seed(1L).build());
reg.draw("gacha");
WeightedRandomReplayer replayer = reg.replay("gacha", 1000L);
```

### SPI（业务自行实现持久化）

| SPI | 用途 |
|-----|------|
| `WeightedRandomPersistence` | 配置与计数：`loadConfig` / `saveCounters` |
| `WeightedRandomReplayStore` | 抽取日志：`appendDrawEvents` / `loadDrawEvents` |
| `WeightedRandomObserver` | 观测：`onDraw` / `onMetricsReport` / `onReplayStep` |

**flush 策略**：`draw()` 热路径不写库；journal 缓冲满或 `flush()` 时批量刷盘；JVM 退出前建议 `KsetRandomRegistry.getInstance().flushAll()`。

**重放模式**：

- **种子重放**：`replayEnabled=true` + 固定 `seed` → `previewSequence(n)` / `replayFromSeed(n)`
- **日志重放**：`journalEnabled=true` + `ReplayStore` → `registry.replay(name, fromSeq)`（只读，不污染 live 计数）

单测可参考 `InMemoryRandomStorage`（test 包）。

## 签名（`com.kset.common.utils.sign`）

OpenAPI 常见规则：参数按 key 字典序拼接为 `secret + key1value1key2value2... + secret`，再做 SHA-1 或 MD5（与 bobo `BoBoSignUtil` 兼容）。

```java
import com.kset.common.utils.sign.KsetSignUtil;

KsetSignUtil signer = KsetSignUtil.of("app-secret");
Map<String, String> params = new LinkedHashMap<>();
params.put("appId", "10001");
params.put("timestamp", "1710000000");

String sign = signer.signSha1(params);   // 或 signMd5 / sign()
params.put("sign", sign);
boolean ok = signer.verifySha1(params);  // 或 checkSign()
```

## 依赖

业务工程通过任意 `kset-starter-*` 间接依赖 `kset-common`，也可直接声明：

```xml
<dependency>
    <groupId>com.kset</groupId>
    <artifactId>kset-common</artifactId>
</dependency>
```

`kset-boot-parent` BOM 已管理 OkHttp、Guava、commons-lang3 等版本，**无需** 再引入 `joda-time`。
## KsetContext（`com.kset.common.context`）

统一请求上下文门面，底层使用 Alibaba `TransmittableThreadLocal`，用于在当前请求、线程池任务和 RPC 调用中承载登录态、trace、灰度、租户、语言等轻量上下文。

```java
KsetContext.put(KsetContextKeys.LOGIN_USER, loginUser);
KsetContext.put(KsetContextKeys.TRACE_ID, traceId);

LoginUser user = KsetContext.get(KsetContextKeys.LOGIN_USER).orElse(null);
KsetContextSnapshot snapshot = KsetContext.capture();

try (KsetContextScope ignored = KsetContext.openScope(snapshot)) {
    // async / rpc work
}
```

`LoginContext` 已兼容委托到 `KsetContextKeys.LOGIN_USER`；业务原有 `LoginContext.requireUser()`、`LoginContext.capture()` 可继续使用。Redis 只作为登录 session 存储，不作为通用上下文传播底座。

多业务隔离约定：

- 公共语义使用 `KsetContextKeys`，如 `LOGIN_USER`、`TRACE_ID`、`TENANT_ID`。
- 业务自定义 key 必须带命名空间，避免覆盖其他业务字段。
- 同名 key 如果类型、传播标记或敏感标记不同，注册时会直接失败。
- `propagatable=false` 的 key 只在当前线程内使用，不会进入 `capture()` 和线程池传播快照。

```java
public static final KsetContextKey<String> ORDER_ID =
        KsetContextKey.of("order", "currentOrderId", String.class);

public static final KsetContextKey<String> CMS_OPERATOR =
        KsetContextKey.of("cms", "operator", String.class);

public static final KsetContextKey<String> LOCAL_TEMP =
        KsetContextKey.of("order", "localTemp", String.class, false, false);
```
